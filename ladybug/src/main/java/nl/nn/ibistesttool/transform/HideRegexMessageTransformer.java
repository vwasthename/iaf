/*
   Copyright 2018 Nationale-Nederlanden

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.ibistesttool.transform;

import java.util.regex.Pattern;

import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.LogUtil;
import nl.nn.adapterframework.util.Misc;
import nl.nn.testtool.transform.MessageTransformer;

/**
 * Hide the same data as is hidden in the Ibis logfiles based on the
 * log.hideRegex property in log4j4ibis.properties.
 * 
 * @author Jaco de Groot
 */
public class HideRegexMessageTransformer implements MessageTransformer {
	Pattern hideRegexPattern = null;
	private final int maxMessageLength = AppConstants.getInstance().getInt("ibistesttool.maxMessageLength", 1024);
	private final boolean limitMessageOnlyWhenHideRegexIsUsed = AppConstants.getInstance().getBoolean("ibistesttool.limitMessageWhenHideRegexIsUsed", false);
	
	HideRegexMessageTransformer() {
		hideRegexPattern = LogUtil.getLog4jHideRegex();
	}

	public String transform(String message) {
		if (maxMessageLength >= 0
				&& message.length() > maxMessageLength) {
			message = message.substring(0, maxMessageLength) + "...(" + (message.length() - maxMessageLength) + " characters more)";
		}
		if (message != null) {
			if (hideRegexPattern != null) {
				message = Misc.hideAll(message, hideRegexPattern);
			}

			Pattern threadHideRegex = LogUtil.getThreadHideRegex();
			if (threadHideRegex != null) {
				message = Misc.hideAll(message, threadHideRegex);
			}
		}
		return message;
	}

	public Pattern getHideRegex() {
		return hideRegexPattern;
	}

	public void setHideRegex(Pattern pattern) {
		hideRegexPattern = pattern;
	}

}
