/*
 * Copyright (C) 2010-2012 LShift Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lshift.diffa.schema.migrations;

/**
 * This exception should be thrown when a migration step should not be applied because the version
 * of the system is incompatible with the step.
 */
public class UpgradeNotSupportedException extends Exception {
  public UpgradeNotSupportedException(Integer newVersionId, String newVersionName, Integer oldVersion) {
    super(String.format("Migration to version %d (%s) is not supported from version %d",
        newVersionId, newVersionName, oldVersion));
  }
}
