/**
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

package net.lshift.diffa.config;

import java.util.HashMap;
import java.util.Map;

public class DateTimeType {
	public static DateTimeType DATE = new DateTimeType("date");
	public static DateTimeType DATETIME = new DateTimeType("datetime");
	private static Map<String, DateTimeType> nameMap = new HashMap<String, DateTimeType>();

	static {
		nameMap.put("date", DATE);
		nameMap.put("datetime", DATETIME);
	}

	public static DateTimeType byName(String name) {
		return nameMap.get(name);
	}

	private String typeName;

	private DateTimeType(String typeName) {
		this.typeName = typeName;
	}

	@Override
	public String toString() {
		return typeName;
	}
}
