#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <dlfcn.h>
#include <string>
#include <unistd.h>
#include <vector>

namespace {

using PdfDocument = void*;
using CreateDocument = PdfDocument (*)();
using CloseDocument = void (*)(PdfDocument);
using ImportPages = int (*)(PdfDocument, PdfDocument, const char*, int);
using ImportPagesByIndex = int (*)(PdfDocument, PdfDocument, const int*, unsigned long, int);
using GetPageCount = int (*)(PdfDocument);
using PdfPage = void*;
using LoadPage = PdfPage (*)(PdfDocument, int);
using ClosePage = void (*)(PdfPage);
using GetPageRotation = int (*)(PdfPage);
using SetPageRotation = void (*)(PdfPage, int);

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
Java_com_rameshta_quietpdf_pdf_NativePdfPageRotator_rotatePages(
    JNIEnv* environment,
    jobject,
    jlong sourcePointer,
    jintArray selectedPageIndices,
    jint quarterTurnsClockwise,
    jint outputFileDescriptor
) {
    if (sourcePointer == 0 || selectedPageIndices == nullptr || outputFileDescriptor < 0 ||
        quarterTurnsClockwise < 1 || quarterTurnsClockwise > 3) return -1;
    const jsize selectedPageCount = environment->GetArrayLength(selectedPageIndices);
    if (selectedPageCount <= 0) return -2;

    void* library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return -3;
    const auto createDocument = loadSymbol<CreateDocument>(library, "FPDF_CreateNewDocument");
    const auto closeDocument = loadSymbol<CloseDocument>(library, "FPDF_CloseDocument");
    const auto importPages = loadSymbol<ImportPages>(library, "FPDF_ImportPages");
    const auto getPageCount = loadSymbol<GetPageCount>(library, "FPDF_GetPageCount");
    const auto loadPage = loadSymbol<LoadPage>(library, "FPDF_LoadPage");
    const auto closePage = loadSymbol<ClosePage>(library, "FPDF_ClosePage");
    const auto getPageRotation = loadSymbol<GetPageRotation>(library, "FPDFPage_GetRotation");
    const auto setPageRotation = loadSymbol<SetPageRotation>(library, "FPDFPage_SetRotation");
    const auto saveAsCopy = loadSymbol<SaveAsCopy>(library, "FPDF_SaveAsCopy");
    if (createDocument == nullptr || closeDocument == nullptr || importPages == nullptr ||
        getPageCount == nullptr || loadPage == nullptr || closePage == nullptr ||
        getPageRotation == nullptr || setPageRotation == nullptr || saveAsCopy == nullptr) {
        dlclose(library);
        return -4;
    }

    auto* wrappedSource = reinterpret_cast<PdfiumAndroidDocument*>(
        static_cast<intptr_t>(sourcePointer)
    );
    const PdfDocument source = wrappedSource->document;
    const int sourcePageCount = source == nullptr ? 0 : getPageCount(source);
    jint* indices = environment->GetIntArrayElements(selectedPageIndices, nullptr);
    if (sourcePageCount <= 0 || indices == nullptr) {
        if (indices != nullptr) {
            environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
        }
        dlclose(library);
        return -5;
    }
    int previousIndex = -1;
    for (jsize index = 0; index < selectedPageCount; ++index) {
        if (indices[index] <= previousIndex || indices[index] < 0 ||
            indices[index] >= sourcePageCount) {
            environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
            dlclose(library);
            return -6;
        }
        previousIndex = indices[index];
    }

    PdfDocument destination = createDocument();
    if (destination == nullptr) {
        environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
        dlclose(library);
        return -7;
    }
    int result = importPages(destination, source, nullptr, 0) == 0 ? -8 : 0;
    if (result == 0 && getPageCount(destination) != sourcePageCount) result = -9;
    for (jsize index = 0; result == 0 && index < selectedPageCount; ++index) {
        PdfPage page = loadPage(destination, indices[index]);
        if (page == nullptr) {
            result = -10;
            break;
        }
        const int currentRotation = getPageRotation(page);
        setPageRotation(page, (currentRotation + quarterTurnsClockwise) % 4);
        closePage(page);
    }
    environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
    if (result == 0) {
        if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 ||
            ftruncate(outputFileDescriptor, 0) < 0) {
            result = -11;
        } else {
            OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
            if (saveAsCopy(destination, &writer.base, 0) == 0) result = -12;
            else if (fsync(outputFileDescriptor) != 0) result = -13;
        }
    }
    closeDocument(destination);
    dlclose(library);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfPageRearranger_rearrangePages(
    JNIEnv* environment,
    jobject,
    jlong sourcePointer,
    jintArray pageOrder,
    jint outputFileDescriptor
) {
    if (sourcePointer == 0 || pageOrder == nullptr || outputFileDescriptor < 0) return -1;
    const jsize pageCount = environment->GetArrayLength(pageOrder);
    if (pageCount < 2) return -2;

    void* library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return -3;
    const auto createDocument = loadSymbol<CreateDocument>(library, "FPDF_CreateNewDocument");
    const auto closeDocument = loadSymbol<CloseDocument>(library, "FPDF_CloseDocument");
    const auto importPagesByIndex = loadSymbol<ImportPagesByIndex>(
        library,
        "FPDF_ImportPagesByIndex"
    );
    const auto getPageCount = loadSymbol<GetPageCount>(library, "FPDF_GetPageCount");
    const auto saveAsCopy = loadSymbol<SaveAsCopy>(library, "FPDF_SaveAsCopy");
    if (createDocument == nullptr || closeDocument == nullptr || importPagesByIndex == nullptr ||
        getPageCount == nullptr || saveAsCopy == nullptr) {
        dlclose(library);
        return -4;
    }

    auto* wrappedSource = reinterpret_cast<PdfiumAndroidDocument*>(
        static_cast<intptr_t>(sourcePointer)
    );
    const PdfDocument source = wrappedSource->document;
    const int sourcePageCount = source == nullptr ? 0 : getPageCount(source);
    if (sourcePageCount != pageCount) {
        dlclose(library);
        return -5;
    }
    jint* indices = environment->GetIntArrayElements(pageOrder, nullptr);
    if (indices == nullptr) {
        dlclose(library);
        return -6;
    }
    std::vector<bool> seen(static_cast<std::size_t>(pageCount), false);
    bool valid = true;
    for (jsize index = 0; index < pageCount; ++index) {
        const jint sourceIndex = indices[index];
        if (sourceIndex < 0 || sourceIndex >= pageCount || seen[sourceIndex]) {
            valid = false;
            break;
        }
        seen[sourceIndex] = true;
    }
    if (!valid) {
        environment->ReleaseIntArrayElements(pageOrder, indices, JNI_ABORT);
        dlclose(library);
        return -7;
    }

    PdfDocument destination = createDocument();
    if (destination == nullptr) {
        environment->ReleaseIntArrayElements(pageOrder, indices, JNI_ABORT);
        dlclose(library);
        return -8;
    }
    int result = importPagesByIndex(
        destination,
        source,
        indices,
        static_cast<unsigned long>(pageCount),
        0
    ) == 0 ? -9 : 0;
    environment->ReleaseIntArrayElements(pageOrder, indices, JNI_ABORT);
    if (result == 0) {
        if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 ||
            ftruncate(outputFileDescriptor, 0) < 0) {
            result = -10;
        } else {
            OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
            if (saveAsCopy(destination, &writer.base, 0) == 0) result = -11;
            else if (fsync(outputFileDescriptor) != 0) result = -12;
        }
    }
    closeDocument(destination);
    dlclose(library);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfPageExtractor_extractPages(
    JNIEnv* environment,
    jobject,
    jlong sourcePointer,
    jintArray selectedPageIndices,
    jint outputFileDescriptor
) {
    if (sourcePointer == 0 || selectedPageIndices == nullptr || outputFileDescriptor < 0) return -1;
    const jsize selectedPageCount = environment->GetArrayLength(selectedPageIndices);
    if (selectedPageCount <= 0) return -2;

    void* library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return -3;
    const auto createDocument = loadSymbol<CreateDocument>(library, "FPDF_CreateNewDocument");
    const auto closeDocument = loadSymbol<CloseDocument>(library, "FPDF_CloseDocument");
    const auto importPagesByIndex = loadSymbol<ImportPagesByIndex>(
        library,
        "FPDF_ImportPagesByIndex"
    );
    const auto getPageCount = loadSymbol<GetPageCount>(library, "FPDF_GetPageCount");
    const auto saveAsCopy = loadSymbol<SaveAsCopy>(library, "FPDF_SaveAsCopy");
    if (createDocument == nullptr || closeDocument == nullptr || importPagesByIndex == nullptr ||
        getPageCount == nullptr || saveAsCopy == nullptr) {
        dlclose(library);
        return -4;
    }

    auto* wrappedSource = reinterpret_cast<PdfiumAndroidDocument*>(
        static_cast<intptr_t>(sourcePointer)
    );
    const PdfDocument source = wrappedSource->document;
    const int sourcePageCount = source == nullptr ? 0 : getPageCount(source);
    jint* indices = environment->GetIntArrayElements(selectedPageIndices, nullptr);
    if (indices == nullptr) {
        dlclose(library);
        return -5;
    }
    int previousIndex = -1;
    bool valid = true;
    for (jsize index = 0; index < selectedPageCount; ++index) {
        if (indices[index] <= previousIndex || indices[index] < 0 ||
            indices[index] >= sourcePageCount) {
            valid = false;
            break;
        }
        previousIndex = indices[index];
    }
    if (!valid) {
        environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
        dlclose(library);
        return -6;
    }

    PdfDocument destination = createDocument();
    if (destination == nullptr) {
        environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
        dlclose(library);
        return -7;
    }
    int result = importPagesByIndex(
        destination,
        source,
        indices,
        static_cast<unsigned long>(selectedPageCount),
        0
    ) == 0 ? -8 : 0;
    environment->ReleaseIntArrayElements(selectedPageIndices, indices, JNI_ABORT);
    if (result == 0) {
        if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 ||
            ftruncate(outputFileDescriptor, 0) < 0) {
            result = -9;
        } else {
            OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
            if (saveAsCopy(destination, &writer.base, 0) == 0) result = -10;
            else if (fsync(outputFileDescriptor) != 0) result = -11;
        }
    }
    closeDocument(destination);
    dlclose(library);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfSplitter_splitPart(
    JNIEnv*,
    jobject,
    jlong sourcePointer,
    jint firstPageIndex,
    jint pageCount,
    jint outputFileDescriptor
) {
    if (sourcePointer == 0 || firstPageIndex < 0 || pageCount <= 0 || outputFileDescriptor < 0) {
        return -1;
    }
    void* library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) return -2;

    const auto createDocument = loadSymbol<CreateDocument>(library, "FPDF_CreateNewDocument");
    const auto closeDocument = loadSymbol<CloseDocument>(library, "FPDF_CloseDocument");
    const auto importPages = loadSymbol<ImportPages>(library, "FPDF_ImportPages");
    const auto getPageCount = loadSymbol<GetPageCount>(library, "FPDF_GetPageCount");
    const auto saveAsCopy = loadSymbol<SaveAsCopy>(library, "FPDF_SaveAsCopy");
    if (createDocument == nullptr || closeDocument == nullptr || importPages == nullptr ||
        getPageCount == nullptr || saveAsCopy == nullptr) {
        dlclose(library);
        return -3;
    }

    auto* wrappedSource = reinterpret_cast<PdfiumAndroidDocument*>(
        static_cast<intptr_t>(sourcePointer)
    );
    const PdfDocument source = wrappedSource->document;
    const int sourcePageCount = source == nullptr ? 0 : getPageCount(source);
    if (firstPageIndex > sourcePageCount - pageCount) {
        dlclose(library);
        return -4;
    }

    PdfDocument destination = createDocument();
    if (destination == nullptr) {
        dlclose(library);
        return -5;
    }
    const int firstPageNumber = firstPageIndex + 1;
    const int lastPageNumber = firstPageIndex + pageCount;
    const std::string pageRange = pageCount == 1
        ? std::to_string(firstPageNumber)
        : std::to_string(firstPageNumber) + "-" + std::to_string(lastPageNumber);
    int result = importPages(destination, source, pageRange.c_str(), 0) == 0 ? -6 : 0;
    if (result == 0) {
        if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 ||
            ftruncate(outputFileDescriptor, 0) < 0) {
            result = -7;
        } else {
            OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
            if (saveAsCopy(destination, &writer.base, 0) == 0) result = -8;
            else if (fsync(outputFileDescriptor) != 0) result = -9;
        }
    }
    closeDocument(destination);
    dlclose(library);
    return result;
}

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
