// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.sdk;

public class VerifierError extends Exception {
    private static final long serialVersionUID = 9099882918121440945L;

    public VerifierError(String message, Throwable cause) {
        super(message, cause);
    }

    public VerifierError(String message) {
        super(message);
    }
}
