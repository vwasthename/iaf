/*
   Copyright 2013 Nationale-Nederlanden, 2021 WeAreFrank!

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
package nl.nn.adapterframework.parameters;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.ParameterException;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.util.Counter;


/**
 * List of parameters.
 * 
 * @author Gerrit van Brakel
 */
public class ParameterList implements Iterable<Parameter> {
	private Map<String, Parameter> map = new LinkedHashMap<>();
	private Counter count = new Counter(0);

	public ParameterList() {
		super();
	}

	public void configure() throws ConfigurationException {
		for(Parameter param : this) {
			param.configure();
			//set name?
		}
	}

	public void add(Parameter parameter) {
		if(parameter == null) {
			throw new IllegalStateException("No parameter defined");
		}

		map.put(parameter.getName(), parameter);
	}

	public boolean contains(String name) {
		return map.containsKey(name);
	}

	@Deprecated
	public Parameter getParameter(int i) {
		int index = 0;
		for(Parameter p : this) {
			if(i == index) {
				return p;
			}
			index++;
		}
		return null;
	}

	public Parameter findParameter(String name) {
		return map.get(name);
	}

	public boolean parameterEvaluationRequiresInputMessage() {
		for (Parameter p:this) {
			if (p.requiresInputValueForResolution()) {
				return true;
			}
		}
		return false;
	}

	public ParameterValueList getValues(Message message, PipeLineSession session) throws ParameterException {
		return getValues(message, session, true);
	}
	/**
	 * Returns an array list of <link>ParameterValue<link> objects
	 */
	public ParameterValueList getValues(Message message, PipeLineSession session, boolean namespaceAware) throws ParameterException {
		ParameterValueList result = new ParameterValueList();
		for (Parameter parm : this) {
			String parmSessionKey = parm.getSessionKey();
			// if a parameter has sessionKey="*", then a list is generated with a synthetic parameter referring to 
			// each session variable whose name starts with the name of the original parameter
			if ("*".equals(parmSessionKey)) {
				String parmName = parm.getName();
				for (String sessionKey: session.keySet()) {
					if (!PipeLineSession.TS_RECEIVED_KEY.equals(sessionKey) && !PipeLineSession.TS_SENT_KEY.equals(sessionKey)) {
						if ((sessionKey.startsWith(parmName) || "*".equals(parmName))) {
							Parameter newParm = new Parameter();
							newParm.setName(sessionKey);
							newParm.setSessionKey(sessionKey); // TODO: Should also set the parameter.type, based on the type of the session key.
							try {
								newParm.configure();
							} catch (ConfigurationException e) {
								throw new ParameterException(e);
							}
							result.add(getValue(result, newParm, message, session, namespaceAware));
						}
					}
				}
			} else {
				result.add(getValue(result, parm, message, session, namespaceAware));
			}
		}
		return result;
	}

	private ParameterValue getValue(ParameterValueList alreadyResolvedParameters, Parameter p, Message message, PipeLineSession session, boolean namespaceAware) throws ParameterException {
		return new ParameterValue(p, p.getValue(alreadyResolvedParameters, message, session, namespaceAware));
	}

	public int size() {
		return map.size();
	}

	@Override
	public Iterator<Parameter> iterator() {
		return map.values().iterator();
	}

	protected Parameter get(String name) {
		return map.get(name);
	}

	protected Parameter remove(String name) {
		return map.remove(name);
	}
}
