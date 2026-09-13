// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package testapplets.library;

import javacard.framework.Shareable;

// Shareable interface for the -useproxyclass path of v3.0.x converters
public interface SomeService extends Shareable {
	short ping(short value);
}
