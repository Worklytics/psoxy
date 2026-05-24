package com.avaulta.gateway.tokens;

import lombok.Getter;

import java.util.function.Function;

/**
 *
 */
public interface ReversibleTokenizationStrategy {


    /**
     * @param originalDatum    to tokenize
     * @param canonicalization used to consistently tokenize datums that are 'canonically
     *                         equivalent'; not byte-wise equal, but are intended to reference
     *                         the same thing - differences are formatting
     * @return hash of canonicalized dataum + encrypted form of originalDatum, that can potentially
     * be reversed back to originalDatum
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     */
    byte[] getReversibleToken(String originalDatum, Function<String, String> canonicalization);

    /**
     * @param originalDatum    to tokenize
     * @return hash of dataum + encrypted form of originalDatum, that can potentially
     * be reversed back to originalDatum
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     */
    default byte[] getReversibleToken(String originalDatum) {
        return getReversibleToken(originalDatum, Function.identity());
    }

    /**
     *
     * @param reversibleToken ciphertext, if it was created with this TokenizationStrategy
     * @return plaintext that was originally passed to this TokenizationStrategy
     */
    String getOriginalDatum(byte[] reversibleToken) throws InvalidTokenException;


    /**
     * Indicates that the token could not be reversed, likely because it is invalid.
     */
    class InvalidTokenException extends RuntimeException {

        @Getter
        public enum ErrorCode {
            ALGORITHM_PARAMETER_ERROR("ITE01", "Failed to decrypt token; some algorithm parameter, such as iv, is wrong"),
            BAD_PADDING("ITE02", "Failed to decrypt token; token appears to be corrupted or invalid, due to mismatch between token's padding and the padding expected by the cipher mode"),
            ILLEGAL_BLOCK_SIZE("ITE003", "Failed to decrypt token; token appears to be corrupted or invalid, as block size seems to differ from expected"),
            DECRYPTION_FAILED("ITE004", "Failed to decrypt token; most likely because encryption key has been rotated");

            private final String code;
            private final String message;

            ErrorCode(String code, String message) {
                this.code = code;
                this.message = message;
            }

        }

        @Getter
        private final ErrorCode errorCode;

        public InvalidTokenException(ErrorCode errorCode, Throwable cause) {
            super("[" + errorCode.getCode() + "] " + errorCode.getMessage(), cause);
            this.errorCode = errorCode;
        }
    }
}
