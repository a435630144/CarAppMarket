package asia.carmix.market.api;

/**
 * API 错误：携带服务端 message（NestJS 错误体 {statusCode, message}）。
 */
public class ApiException extends Exception {

    public final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }
}
