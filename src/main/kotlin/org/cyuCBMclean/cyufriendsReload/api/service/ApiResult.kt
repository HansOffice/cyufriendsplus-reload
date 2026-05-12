package org.cyuCBMclean.cyufriendsReload.api.service

enum class ApiResultCode {
    SUCCESS,
    PENDING,
    MODULE_DISABLED,
    INVALID_ARGUMENT,
    SAME_PLAYER,
    NOT_FOUND,
    ALREADY_FRIENDS,
    NOT_FRIENDS,
    BLOCKED,
    REQUEST_EXISTS,
    REQUEST_NOT_FOUND,
    EMPTY_CONTENT,
    COOLDOWN,
    LIMIT_REACHED,
    FORBIDDEN,
    ALREADY_REACTED,
    NOT_REACTED,
    FAILED
}

data class ApiResult(
    val code: ApiResultCode,
    val message: String = "",
    val value: String? = null
) {
    val success: Boolean
        get() = code == ApiResultCode.SUCCESS || code == ApiResultCode.PENDING

    companion object {
        @JvmStatic
        fun success(value: String? = null): ApiResult = ApiResult(ApiResultCode.SUCCESS, value = value)

        @JvmStatic
        fun pending(value: String? = null): ApiResult = ApiResult(ApiResultCode.PENDING, value = value)

        @JvmStatic
        fun fail(code: ApiResultCode, message: String = "", value: String? = null): ApiResult {
            return ApiResult(code, message, value)
        }
    }
}
