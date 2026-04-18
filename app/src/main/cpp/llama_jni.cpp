#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include "ggml.h"

#define TAG "LlamaCppBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaContext {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    int n_ctx = 2048;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_opendash_app_assistant_provider_embedded_LlamaCppBridge_nativeLoadModel(
    JNIEnv *env, jobject thiz,
    jstring path, jint contextSize, jint threads, jint gpuLayers) {

    const char *model_path = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading model: %s (ctx=%d, threads=%d, gpu=%d)", model_path, contextSize, threads, gpuLayers);

    llama_backend_init();

    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = gpuLayers;
    model_params.use_mmap = true;

    auto * lctx = new LlamaContext();
    lctx->n_ctx = contextSize;
    lctx->model = llama_model_load_from_file(model_path, model_params);

    env->ReleaseStringUTFChars(path, model_path);

    if (!lctx->model) {
        LOGE("Failed to load model");
        delete lctx;
        return 0;
    }

    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
    ctx_params.type_k = GGML_TYPE_Q8_0;
    ctx_params.type_v = GGML_TYPE_Q8_0;

    LOGI("Context params: batch=512, flash_attn=true, kv_cache=q8_0");

    lctx->ctx = llama_init_from_model(lctx->model, ctx_params);
    if (!lctx->ctx) {
        LOGE("Failed to create context");
        llama_model_free(lctx->model);
        delete lctx;
        return 0;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(lctx);
}

JNIEXPORT void JNICALL
Java_com_opendash_app_assistant_provider_embedded_LlamaCppBridge_nativeUnloadModel(
    JNIEnv *env, jobject thiz, jlong handle) {

    auto *lctx = reinterpret_cast<LlamaContext*>(handle);
    if (lctx) {
        if (lctx->ctx) llama_free(lctx->ctx);
        if (lctx->model) llama_model_free(lctx->model);
        delete lctx;
        LOGI("Model unloaded");
    }
    llama_backend_free();
}

static std::string generate_impl(LlamaContext *lctx, const char *prompt_str, int max_tokens, float temperature) {
    auto * model = lctx->model;
    auto * ctx = lctx->ctx;
    const auto * vocab = llama_model_get_vocab(model);

    // Tokenize prompt
    std::vector<llama_token> tokens(lctx->n_ctx);
    int n_tokens = llama_tokenize(vocab, prompt_str, strlen(prompt_str),
                                   tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return "[Error: tokenization failed]";
    }
    tokens.resize(n_tokens);
    LOGI("Prompt tokenized: %d tokens", n_tokens);

    // Clear memory
    llama_memory_clear(llama_get_memory(ctx), true);

    // Evaluate prompt using llama_batch_get_one
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(ctx, batch) != 0) {
        LOGE("Prompt decode failed");
        return "[Error: decode failed]";
    }

    // Generate tokens
    std::string result;
    auto * smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));

    for (int i = 0; i < max_tokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_token_is_eog(vocab, new_token)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
            // Stop at Gemma turn marker
            if (result.find("<end_of_turn>") != std::string::npos) {
                result = result.substr(0, result.find("<end_of_turn>"));
                break;
            }
        }

        // Decode next token
        llama_batch next = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx, next) != 0) {
            LOGE("Generation decode failed at token %d", i);
            break;
        }
    }

    llama_sampler_free(smpl);
    LOGI("Generated %zu chars", result.size());
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_opendash_app_assistant_provider_embedded_LlamaCppBridge_nativeGenerate(
    JNIEnv *env, jobject thiz,
    jlong handle, jstring prompt, jint maxTokens, jfloat temperature) {

    auto *lctx = reinterpret_cast<LlamaContext*>(handle);
    if (!lctx || !lctx->ctx) {
        return env->NewStringUTF("[Error: Model not loaded]");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string result = generate_impl(lctx, prompt_str, maxTokens, temperature);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_opendash_app_assistant_provider_embedded_LlamaCppBridge_nativeGenerateStreaming(
    JNIEnv *env, jobject thiz,
    jlong handle, jstring prompt, jint maxTokens, jfloat temperature,
    jobject callback) {

    auto *lctx = reinterpret_cast<LlamaContext*>(handle);
    if (!lctx || !lctx->ctx) {
        return env->NewStringUTF("[Error: Model not loaded]");
    }

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string result = generate_impl(lctx, prompt_str, maxTokens, temperature);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;");
    jstring token = env->NewStringUTF(result.c_str());
    env->CallObjectMethod(callback, invokeMethod, token);
    env->DeleteLocalRef(token);

    return env->NewStringUTF(result.c_str());
}

} // extern "C"
