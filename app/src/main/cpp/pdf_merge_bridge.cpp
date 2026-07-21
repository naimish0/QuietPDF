#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <dlfcn.h>
#include <unistd.h>

namespace {

using PdfDocument = void*;
using CreateDocument = PdfDocument (*)();
using CloseDocument = void (*)(PdfDocument);
using ImportPages = int (*)(PdfDocument, PdfDocument, const char*, int);
using GetPageCount = int (*)(PdfDocument);

struct PdfFileWrite {
    int version;
    int (*writeBlock)(PdfFileWrite*, const void*, unsigned long);
};

using SaveAsCopy = int (*)(PdfDocument, PdfFileWrite*, unsigned long);

// PdfiumAndroidKt exposes a pointer to its native DocumentFile wrapper. The
// wrapped FPDF_DOCUMENT is its first field (pdfiumandroid 2.0.2), so unwrap it
// before calling PDFium's public page-import API.
struct PdfiumAndroidDocument {
    PdfDocument document;
};

struct OutputWriter {
    PdfFileWrite base;
    int fileDescriptor;
};

int writeBlock(PdfFileWrite* writer, const void* data, unsigned long size) {
    auto* output = reinterpret_cast<OutputWriter*>(writer);
    auto* bytes = static_cast<const std::uint8_t*>(data);
    unsigned long written = 0;
    while (written < size) {
        const ssize_t result = write(output->fileDescriptor, bytes + written, size - written);
        if (result < 0 && errno == EINTR) continue;
        if (result <= 0) return 0;
        written += static_cast<unsigned long>(result);
    }
    return 1;
}

template <typename Function>
Function loadSymbol(void* library, const char* name) {
    return reinterpret_cast<Function>(dlsym(library, name));
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfMerger_merge(
    JNIEnv* environment,
    jobject,
    jlongArray sourcePointers,
    jint outputFileDescriptor
) {
    if (sourcePointers == nullptr || outputFileDescriptor < 0) return -1;
    const jsize sourceCount = environment->GetArrayLength(sourcePointers);
    if (sourceCount < 2) return -2;

    void* library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return -3;

    const auto createDocument = loadSymbol<CreateDocument>(library, "FPDF_CreateNewDocument");
    const auto closeDocument = loadSymbol<CloseDocument>(library, "FPDF_CloseDocument");
    const auto importPages = loadSymbol<ImportPages>(library, "FPDF_ImportPages");
    const auto getPageCount = loadSymbol<GetPageCount>(library, "FPDF_GetPageCount");
    const auto saveAsCopy = loadSymbol<SaveAsCopy>(library, "FPDF_SaveAsCopy");
    if (createDocument == nullptr || closeDocument == nullptr || importPages == nullptr ||
        getPageCount == nullptr || saveAsCopy == nullptr) {
        dlclose(library);
        return -4;
    }

    PdfDocument destination = createDocument();
    if (destination == nullptr) {
        dlclose(library);
        return -5;
    }

    jlong* pointers = environment->GetLongArrayElements(sourcePointers, nullptr);
    if (pointers == nullptr) {
        closeDocument(destination);
        dlclose(library);
        return -6;
    }

    int result = 0;
    int destinationPageIndex = 0;
    for (jsize index = 0; index < sourceCount; ++index) {
        auto* wrappedSource = reinterpret_cast<PdfiumAndroidDocument*>(
            static_cast<intptr_t>(pointers[index])
        );
        const PdfDocument source = wrappedSource == nullptr ? nullptr : wrappedSource->document;
        if (source == nullptr || importPages(destination, source, nullptr, destinationPageIndex) == 0) {
            result = -7;
            break;
        }
        destinationPageIndex += getPageCount(source);
    }

    environment->ReleaseLongArrayElements(sourcePointers, pointers, JNI_ABORT);
    if (result == 0) {
        if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 ||
            ftruncate(outputFileDescriptor, 0) < 0) {
            result = -8;
        } else {
            OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
            if (saveAsCopy(destination, &writer.base, 0) == 0) result = -9;
            else if (fsync(outputFileDescriptor) != 0) result = -10;
        }
    }

    closeDocument(destination);
    dlclose(library);
    return result;
}
