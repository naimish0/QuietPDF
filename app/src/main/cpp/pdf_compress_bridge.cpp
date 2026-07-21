#include <jni.h>

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <dlfcn.h>
#include <limits>
#include <unistd.h>
#include <vector>

namespace {

using PdfDocument = void*;
using PdfPage = void*;
using PdfPageObject = void*;
using PdfBitmap = void*;
using ImportPages = int (*)(PdfDocument, PdfDocument, const char*, int);
using CreateDocument = PdfDocument (*)();
using CloseDocument = void (*)(PdfDocument);
using GetPageCount = int (*)(PdfDocument);
using LoadPage = PdfPage (*)(PdfDocument, int);
using ClosePage = void (*)(PdfPage);
using CountObjects = int (*)(PdfPage);
using GetObject = PdfPageObject (*)(PdfPage, int);
using GetObjectType = int (*)(PdfPageObject);
using FormCountObjects = int (*)(PdfPageObject);
using FormGetObject = PdfPageObject (*)(PdfPageObject, unsigned long);
using HasTransparency = int (*)(PdfPageObject);
using GetImagePixelSize = int (*)(PdfPageObject, unsigned int*, unsigned int*);
using GetImageDataRaw = unsigned long (*)(PdfPageObject, void*, unsigned long);
using GetImageBitmap = PdfBitmap (*)(PdfPageObject);
using DestroyBitmap = void (*)(PdfBitmap);
using GetBitmapBuffer = void* (*)(PdfBitmap);
using GetBitmapWidth = int (*)(PdfBitmap);
using GetBitmapHeight = int (*)(PdfBitmap);
using GetBitmapStride = int (*)(PdfBitmap);
using GetBitmapFormat = int (*)(PdfBitmap);

struct PdfFileAccess {
    unsigned long fileLength;
    int (*getBlock)(void*, unsigned long, unsigned char*, unsigned long);
    void* parameter;
};

using LoadJpegInline = int (*)(PdfPage*, int, PdfPageObject, PdfFileAccess*);
using GenerateContent = int (*)(PdfPage);

struct PdfFileWrite {
    int version;
    int (*writeBlock)(PdfFileWrite*, const void*, unsigned long);
};

using SaveAsCopy = int (*)(PdfDocument, PdfFileWrite*, unsigned long);

struct PdfiumAndroidDocument {
    PdfDocument document;
};

struct OutputWriter {
    PdfFileWrite base;
    int fileDescriptor;
};

struct MemoryFile {
    const std::uint8_t* bytes;
    std::size_t size;
};

// Recompression temporarily holds both PDFium's decoded bitmap and an Android bitmap. Keeping
// this below typical Android heap limits is safer than attempting very large camera images and
// risking the whole operation. Images above the limit remain untouched in the output PDF.
constexpr std::uint64_t kMaxRecompressionPixels = 12000000ULL;

struct CompressionApi {
    void* library = nullptr;
    CreateDocument createDocument = nullptr;
    CloseDocument closeDocument = nullptr;
    ImportPages importPages = nullptr;
    GetPageCount getPageCount = nullptr;
    LoadPage loadPage = nullptr;
    ClosePage closePage = nullptr;
    CountObjects countObjects = nullptr;
    GetObject getObject = nullptr;
    GetObjectType getObjectType = nullptr;
    FormCountObjects formCountObjects = nullptr;
    FormGetObject formGetObject = nullptr;
    HasTransparency hasTransparency = nullptr;
    GetImagePixelSize getImagePixelSize = nullptr;
    GetImageDataRaw getImageDataRaw = nullptr;
    GetImageBitmap getImageBitmap = nullptr;
    DestroyBitmap destroyBitmap = nullptr;
    GetBitmapBuffer getBitmapBuffer = nullptr;
    GetBitmapWidth getBitmapWidth = nullptr;
    GetBitmapHeight getBitmapHeight = nullptr;
    GetBitmapStride getBitmapStride = nullptr;
    GetBitmapFormat getBitmapFormat = nullptr;
    LoadJpegInline loadJpegInline = nullptr;
    GenerateContent generateContent = nullptr;
    SaveAsCopy saveAsCopy = nullptr;
};

struct CompressionSession {
    CompressionApi api;
    PdfDocument document = nullptr;
    int pageCount = 0;
};

template <typename Function>
Function loadSymbol(void* library, const char* name) {
    return reinterpret_cast<Function>(dlsym(library, name));
}

bool loadApi(CompressionApi* api) {
    api->library = dlopen("libpdfium.so", RTLD_NOW | RTLD_LOCAL);
    if (api->library == nullptr) return false;
    api->createDocument = loadSymbol<CreateDocument>(api->library, "FPDF_CreateNewDocument");
    api->closeDocument = loadSymbol<CloseDocument>(api->library, "FPDF_CloseDocument");
    api->importPages = loadSymbol<ImportPages>(api->library, "FPDF_ImportPages");
    api->getPageCount = loadSymbol<GetPageCount>(api->library, "FPDF_GetPageCount");
    api->loadPage = loadSymbol<LoadPage>(api->library, "FPDF_LoadPage");
    api->closePage = loadSymbol<ClosePage>(api->library, "FPDF_ClosePage");
    api->countObjects = loadSymbol<CountObjects>(api->library, "FPDFPage_CountObjects");
    api->getObject = loadSymbol<GetObject>(api->library, "FPDFPage_GetObject");
    api->getObjectType = loadSymbol<GetObjectType>(api->library, "FPDFPageObj_GetType");
    api->formCountObjects = loadSymbol<FormCountObjects>(api->library, "FPDFFormObj_CountObjects");
    api->formGetObject = loadSymbol<FormGetObject>(api->library, "FPDFFormObj_GetObject");
    api->hasTransparency = loadSymbol<HasTransparency>(api->library, "FPDFPageObj_HasTransparency");
    api->getImagePixelSize = loadSymbol<GetImagePixelSize>(
        api->library,
        "FPDFImageObj_GetImagePixelSize"
    );
    api->getImageDataRaw = loadSymbol<GetImageDataRaw>(
        api->library,
        "FPDFImageObj_GetImageDataRaw"
    );
    api->getImageBitmap = loadSymbol<GetImageBitmap>(api->library, "FPDFImageObj_GetBitmap");
    api->destroyBitmap = loadSymbol<DestroyBitmap>(api->library, "FPDFBitmap_Destroy");
    api->getBitmapBuffer = loadSymbol<GetBitmapBuffer>(api->library, "FPDFBitmap_GetBuffer");
    api->getBitmapWidth = loadSymbol<GetBitmapWidth>(api->library, "FPDFBitmap_GetWidth");
    api->getBitmapHeight = loadSymbol<GetBitmapHeight>(api->library, "FPDFBitmap_GetHeight");
    api->getBitmapStride = loadSymbol<GetBitmapStride>(api->library, "FPDFBitmap_GetStride");
    api->getBitmapFormat = loadSymbol<GetBitmapFormat>(api->library, "FPDFBitmap_GetFormat");
    api->loadJpegInline = loadSymbol<LoadJpegInline>(
        api->library,
        "FPDFImageObj_LoadJpegFileInline"
    );
    api->generateContent = loadSymbol<GenerateContent>(api->library, "FPDFPage_GenerateContent");
    api->saveAsCopy = loadSymbol<SaveAsCopy>(api->library, "FPDF_SaveAsCopy");
    return api->createDocument != nullptr && api->closeDocument != nullptr &&
        api->importPages != nullptr && api->getPageCount != nullptr && api->loadPage != nullptr &&
        api->closePage != nullptr && api->countObjects != nullptr && api->getObject != nullptr &&
        api->getObjectType != nullptr && api->formCountObjects != nullptr &&
        api->formGetObject != nullptr && api->hasTransparency != nullptr &&
        api->getImagePixelSize != nullptr && api->getImageDataRaw != nullptr &&
        api->getImageBitmap != nullptr && api->destroyBitmap != nullptr &&
        api->getBitmapBuffer != nullptr && api->getBitmapWidth != nullptr &&
        api->getBitmapHeight != nullptr && api->getBitmapStride != nullptr &&
        api->getBitmapFormat != nullptr && api->loadJpegInline != nullptr &&
        api->generateContent != nullptr && api->saveAsCopy != nullptr;
}

void closeApi(CompressionApi* api) {
    if (api->library != nullptr) dlclose(api->library);
    api->library = nullptr;
}

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

int readMemoryBlock(
    void* parameter,
    unsigned long position,
    unsigned char* output,
    unsigned long size
) {
    auto* file = static_cast<MemoryFile*>(parameter);
    if (position > file->size || size > file->size - position) return 0;
    std::memcpy(output, file->bytes + position, size);
    return 1;
}

void collectImages(
    const CompressionApi& api,
    PdfPageObject object,
    std::vector<PdfPageObject>* images,
    int depth = 0
) {
    if (object == nullptr || depth > 16) return;
    const int type = api.getObjectType(object);
    if (type == 3) {
        if (api.hasTransparency(object) == 0) images->push_back(object);
        return;
    }
    if (type != 5) return;
    const int count = api.formCountObjects(object);
    for (int index = 0; index < count; ++index) {
        collectImages(api, api.formGetObject(object, static_cast<unsigned long>(index)), images, depth + 1);
    }
}

std::vector<PdfPageObject> pageImages(const CompressionApi& api, PdfPage page) {
    std::vector<PdfPageObject> images;
    const int count = api.countObjects(page);
    for (int index = 0; index < count; ++index) {
        collectImages(api, api.getObject(page, index), &images);
    }
    return images;
}

bool containsText(const CompressionApi& api, PdfPageObject object, int depth = 0) {
    if (object == nullptr || depth > 16) return false;
    const int type = api.getObjectType(object);
    if (type == 1) return true;
    if (type != 5) return false;
    const int count = api.formCountObjects(object);
    for (int index = 0; index < count; ++index) {
        if (containsText(
                api,
                api.formGetObject(object, static_cast<unsigned long>(index)),
                depth + 1
            )) return true;
    }
    return false;
}

bool pageHasText(const CompressionApi& api, PdfPage page) {
    const int count = api.countObjects(page);
    for (int index = 0; index < count; ++index) {
        if (containsText(api, api.getObject(page, index))) return true;
    }
    return false;
}

jbyteArray encodeJpeg(
    JNIEnv* environment,
    PdfBitmap bitmap,
    const CompressionApi& api,
    int maxDimension,
    int quality
) {
    const int width = api.getBitmapWidth(bitmap);
    const int height = api.getBitmapHeight(bitmap);
    const int stride = api.getBitmapStride(bitmap);
    const int format = api.getBitmapFormat(bitmap);
    void* pixels = api.getBitmapBuffer(bitmap);
    if (width <= 0 || height <= 0 || stride <= 0 || pixels == nullptr ||
        format < 1 || format > 4) return nullptr;
    const auto capacity = static_cast<jlong>(stride) * height;
    jobject buffer = environment->NewDirectByteBuffer(pixels, capacity);
    if (buffer == nullptr) return nullptr;
    jclass compressor = environment->FindClass(
        "com/rameshta/quietpdf/pdf/NativePdfCompressor"
    );
    if (compressor == nullptr) {
        environment->DeleteLocalRef(buffer);
        return nullptr;
    }
    jmethodID method = environment->GetStaticMethodID(
        compressor,
        "encodeJpeg",
        "(Ljava/nio/ByteBuffer;IIIIII)[B"
    );
    if (method == nullptr) {
        environment->DeleteLocalRef(compressor);
        environment->DeleteLocalRef(buffer);
        return nullptr;
    }
    auto result = static_cast<jbyteArray>(environment->CallStaticObjectMethod(
        compressor,
        method,
        buffer,
        width,
        height,
        stride,
        format,
        maxDimension,
        quality
    ));
    environment->DeleteLocalRef(compressor);
    environment->DeleteLocalRef(buffer);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfCompressor_analyzeImages(
    JNIEnv* environment,
    jobject,
    jlong sourcePointer,
    jint expectedPageCount
) {
    if (sourcePointer == 0 || expectedPageCount <= 0) return nullptr;
    CompressionApi api;
    if (!loadApi(&api)) {
        closeApi(&api);
        return nullptr;
    }
    auto* wrapped = reinterpret_cast<PdfiumAndroidDocument*>(static_cast<intptr_t>(sourcePointer));
    const PdfDocument document = wrapped->document;
    if (document == nullptr || api.getPageCount(document) != expectedPageCount) {
        closeApi(&api);
        return nullptr;
    }
    std::vector<jlong> details;
    for (int pageIndex = 0; pageIndex < expectedPageCount; ++pageIndex) {
        PdfPage page = api.loadPage(document, pageIndex);
        if (page == nullptr) {
            closeApi(&api);
            return nullptr;
        }
        const auto images = pageHasText(api, page) ? std::vector<PdfPageObject>() : pageImages(api, page);
        for (PdfPageObject image : images) {
            unsigned int width = 0;
            unsigned int height = 0;
            const unsigned long rawSize = api.getImageDataRaw(image, nullptr, 0);
            const bool hasPixelSize = api.getImagePixelSize(image, &width, &height) != 0;
            const std::uint64_t pixels = static_cast<std::uint64_t>(width) * height;
            if (hasPixelSize && width >= 64 && height >= 64 && rawSize >= 4096 &&
                pixels <= kMaxRecompressionPixels) {
                details.push_back(static_cast<jlong>(rawSize));
                details.push_back(static_cast<jlong>(width));
                details.push_back(static_cast<jlong>(height));
            }
        }
        api.closePage(page);
    }
    closeApi(&api);
    jlongArray result = environment->NewLongArray(static_cast<jsize>(details.size()));
    if (result != nullptr && !details.empty()) {
        environment->SetLongArrayRegion(result, 0, static_cast<jsize>(details.size()), details.data());
    }
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfCompressor_createSession(
    JNIEnv*,
    jobject,
    jlong sourcePointer,
    jint expectedPageCount
) {
    if (sourcePointer == 0 || expectedPageCount <= 0) return 0;
    auto* session = new CompressionSession();
    if (!loadApi(&session->api)) {
        closeApi(&session->api);
        delete session;
        return 0;
    }
    auto* wrapped = reinterpret_cast<PdfiumAndroidDocument*>(static_cast<intptr_t>(sourcePointer));
    const PdfDocument source = wrapped->document;
    if (source == nullptr || session->api.getPageCount(source) != expectedPageCount) {
        closeApi(&session->api);
        delete session;
        return 0;
    }
    session->document = session->api.createDocument();
    if (session->document == nullptr ||
        session->api.importPages(session->document, source, nullptr, 0) == 0 ||
        session->api.getPageCount(session->document) != expectedPageCount) {
        if (session->document != nullptr) session->api.closeDocument(session->document);
        closeApi(&session->api);
        delete session;
        return 0;
    }
    session->pageCount = expectedPageCount;
    return static_cast<jlong>(reinterpret_cast<intptr_t>(session));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfCompressor_compressPage(
    JNIEnv* environment,
    jobject,
    jlong sessionPointer,
    jint pageIndex,
    jint maxDimension,
    jint jpegQuality
) {
    auto* session = reinterpret_cast<CompressionSession*>(static_cast<intptr_t>(sessionPointer));
    if (session == nullptr || pageIndex < 0 || pageIndex >= session->pageCount ||
        maxDimension < 320 || jpegQuality < 1 || jpegQuality > 100) return -1;
    PdfPage page = session->api.loadPage(session->document, pageIndex);
    if (page == nullptr) return -2;
    if (pageHasText(session->api, page)) {
        session->api.closePage(page);
        return 0;
    }
    int replaced = 0;
    for (PdfPageObject image : pageImages(session->api, page)) {
        unsigned int pixelWidth = 0;
        unsigned int pixelHeight = 0;
        const unsigned long originalSize = session->api.getImageDataRaw(image, nullptr, 0);
        if (session->api.getImagePixelSize(image, &pixelWidth, &pixelHeight) == 0 ||
            pixelWidth < 64 || pixelHeight < 64 || originalSize < 4096) continue;
        const std::uint64_t pixels = static_cast<std::uint64_t>(pixelWidth) * pixelHeight;
        if (pixels > kMaxRecompressionPixels) continue;
        PdfBitmap bitmap = session->api.getImageBitmap(image);
        if (bitmap == nullptr) continue;
        jbyteArray jpeg = encodeJpeg(
            environment,
            bitmap,
            session->api,
            maxDimension,
            jpegQuality
        );
        session->api.destroyBitmap(bitmap);
        if (environment->ExceptionCheck()) {
            session->api.closePage(page);
            return -3;
        }
        if (jpeg == nullptr) continue;
        const jsize jpegSize = environment->GetArrayLength(jpeg);
        if (jpegSize <= 0 || static_cast<unsigned long>(jpegSize + 256) >= originalSize) {
            environment->DeleteLocalRef(jpeg);
            continue;
        }
        jbyte* bytes = environment->GetByteArrayElements(jpeg, nullptr);
        if (bytes == nullptr) {
            environment->DeleteLocalRef(jpeg);
            session->api.closePage(page);
            return -4;
        }
        MemoryFile memory{reinterpret_cast<std::uint8_t*>(bytes), static_cast<std::size_t>(jpegSize)};
        PdfFileAccess access{
            static_cast<unsigned long>(jpegSize),
            readMemoryBlock,
            &memory,
        };
        const int loaded = session->api.loadJpegInline(nullptr, 0, image, &access);
        environment->ReleaseByteArrayElements(jpeg, bytes, JNI_ABORT);
        environment->DeleteLocalRef(jpeg);
        if (loaded == 0) {
            session->api.closePage(page);
            return -5;
        }
        ++replaced;
    }
    if (replaced > 0 && session->api.generateContent(page) == 0) {
        session->api.closePage(page);
        return -6;
    }
    session->api.closePage(page);
    return replaced;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfCompressor_saveSession(
    JNIEnv*,
    jobject,
    jlong sessionPointer,
    jint outputFileDescriptor
) {
    auto* session = reinterpret_cast<CompressionSession*>(static_cast<intptr_t>(sessionPointer));
    if (session == nullptr || outputFileDescriptor < 0) return -1;
    PdfDocument compact = session->api.createDocument();
    if (compact == nullptr) return -2;
    if (session->api.importPages(compact, session->document, nullptr, 0) == 0 ||
        session->api.getPageCount(compact) != session->pageCount) {
        session->api.closeDocument(compact);
        return -3;
    }
    if (lseek(outputFileDescriptor, 0, SEEK_SET) < 0 || ftruncate(outputFileDescriptor, 0) < 0) {
        session->api.closeDocument(compact);
        return -4;
    }
    OutputWriter writer{{1, writeBlock}, outputFileDescriptor};
    const int saved = session->api.saveAsCopy(compact, &writer.base, 2);
    session->api.closeDocument(compact);
    if (saved == 0) return -5;
    return fsync(outputFileDescriptor) == 0 ? 0 : -6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_rameshta_quietpdf_pdf_NativePdfCompressor_closeSession(
    JNIEnv*,
    jobject,
    jlong sessionPointer
) {
    auto* session = reinterpret_cast<CompressionSession*>(static_cast<intptr_t>(sessionPointer));
    if (session == nullptr) return;
    if (session->document != nullptr) session->api.closeDocument(session->document);
    closeApi(&session->api);
    delete session;
}
