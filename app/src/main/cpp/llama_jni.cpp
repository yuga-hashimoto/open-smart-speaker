/**
 * JNI bridge for llama.cpp
 *
 * Build requirements:
 * 1. Install Android NDK via SDK Manager
 * 2. Clone llama.cpp into app/src/main/cpp/llama.cpp/
 *    git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
 * 3. Build will automatically compile via CMake
 */

#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LlamaCppBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations - these will link against llama.cpp library
// When llama.cpp submodule is present, uncomment and use actual headers:
// #include "llama.cpp/include/llama.h"
// #include "llama.cpp/include/common.h"

struct LlamaContext {
    // llama_model * model = nullptr;
    // llama_context * ctx = nullptr;
    void * model = nullptr;
    void * ctx = nullptr;
    int n_ctx = 2048;
    bool loaded = false;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_opensmarthome_speaker_assistant_provider_embedded_LlamaCppBridge_nativeLoadModel(
    JNIEnv *env, jobject thiz,
    jstring path, jint contextSize, jint threads, jint gpuLayers) {

    const char *model_path = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading model: %s (ctx=%d, threads=%d, gpu=%d)", model_path, contextSize, threads, gpuLayers);

    auto *ctx = new LlamaContext();
    ctx->n_ctx = contextSize;

    // TODO: When llama.cpp submodule is present, implement actual model loading:
    // llama_backend_init();
    // auto params = llama_model_default_params();
    // params.n_gpu_layers = gpuLayers;
    // ctx->model = llama_load_model_from_file(model_path, params);
    // auto cparams = llama_context_default_params();
    // cparams.n_ctx = contextSize;
    // cparams.n_threads = threads;
    // ctx->ctx = llama_new_context_with_model(ctx->model, cparams);
    // ctx->loaded = (ctx->model != nullptr && ctx->ctx != nullptr);

    // Stub: mark as loaded if file exists
    FILE *f = fopen(model_path, "rb");
    if (f) {
        ctx->loaded = true;
        fclose(f);
        LOGI("Model file found, stub loaded");
    } else {
        LOGE("Model file not found: %s", model_path);
    }

    env->ReleaseStringUTFChars(path, model_path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_opensmarthome_speaker_assistant_provider_embedded_LlamaCppBridge_nativeUnloadModel(
    JNIEnv *env, jobject thiz, jlong handle) {

    auto *ctx = reinterpret_cast<LlamaContext*>(handle);
    if (ctx) {
        // TODO: llama_free(ctx->ctx); llama_free_model(ctx->model);
        delete ctx;
        LOGI("Model unloaded");
    }
}

JNIEXPORT jstring JNICALL
Java_com_opensmarthome_speaker_assistant_provider_embedded_LlamaCppBridge_nativeGenerate(
    JNIEnv *env, jobject thiz,
    jlong handle, jstring prompt, jint maxTokens, jfloat temperature) {

    auto *ctx = reinterpret_cast<LlamaContext*>(handle);
    if (!ctx || !ctx->loaded) {
        return env->NewStringUTF("[Error: Model not loaded]");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generate: prompt_len=%zu, max_tokens=%d, temp=%.2f",
         strlen(prompt_str), maxTokens, temperature);

    // TODO: Implement actual generation with llama.cpp
    // 1. Tokenize prompt
    // 2. Evaluate tokens
    // 3. Sample and decode tokens in loop
    // 4. Return completed text

    // Stub response for testing
    std::string response = "I'm the on-device AI assistant. The llama.cpp inference engine is set up but needs the model library to be compiled with NDK. Please install Android NDK and run: git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp";

    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_opensmarthome_speaker_assistant_provider_embedded_LlamaCppBridge_nativeGenerateStreaming(
    JNIEnv *env, jobject thiz,
    jlong handle, jstring prompt, jint maxTokens, jfloat temperature,
    jobject callback) {

    auto *ctx = reinterpret_cast<LlamaContext*>(handle);
    if (!ctx || !ctx->loaded) {
        return env->NewStringUTF("[Error: Model not loaded]");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;");

    // TODO: Implement streaming with llama.cpp
    // For each generated token:
    // 1. Sample next token
    // 2. Decode to string
    // 3. Call callback with token string
    // 4. If callback returns false, stop

    // Stub: send tokens one by one
    const char* tokens[] = {"Hello", " from", " on-device", " LLM!", nullptr};
    std::string full_response;

    for (int i = 0; tokens[i] != nullptr; i++) {
        jstring token = env->NewStringUTF(tokens[i]);
        jobject result = env->CallObjectMethod(callback, invokeMethod, token);

        full_response += tokens[i];

        // Check if callback returned false (stop generation)
        if (result != nullptr) {
            jclass boolClass = env->FindClass("java/lang/Boolean");
            jmethodID boolValue = env->GetMethodID(boolClass, "booleanValue", "()Z");
            jboolean shouldContinue = env->CallBooleanMethod(result, boolValue);
            if (!shouldContinue) break;
        }

        env->DeleteLocalRef(token);
    }

    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(full_response.c_str());
}

} // extern "C"
