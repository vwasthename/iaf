/*
   Copyright 2021 WeAreFrank!

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
package nl.nn.adapterframework.configuration.digester;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;

import nl.nn.adapterframework.configuration.ConfigurationWarning;
import nl.nn.adapterframework.configuration.HasSpecialDefaultValues;
import nl.nn.adapterframework.configuration.SuppressKeys;
import nl.nn.adapterframework.doc.ProtectedAttribute;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.EnumUtils;
import nl.nn.adapterframework.util.StringResolver;

/**
 * @author Niels Meijer
 */
public class ValidateAttributeRule extends DigesterRuleBase {
	private boolean suppressDeprecationWarnings = AppConstants.getInstance().getBoolean(SuppressKeys.DEPRECATION_SUPPRESS_KEY.getKey(), false);

	@Override
	protected void handleBean() {
		if(!suppressDeprecationWarnings) {
			Class<?> clazz = getBeanClass();
			ConfigurationWarning warning = AnnotationUtils.findAnnotation(clazz, ConfigurationWarning.class);
			if(warning != null) {
				String msg = "";
				if(AnnotationUtils.findAnnotation(clazz, Deprecated.class) != null) {
					msg += "is deprecated";
				}
				if(StringUtils.isNotEmpty(warning.value())) {
					msg += ": "+warning.value();
				}

				addLocalWarning(msg);
			}
		}
	}

	@Override
	protected void handleAttribute(String name, String value, Map<String, String> attributes) throws Exception {
		PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(getBean(), name);
		Method m=null;
		if (pd!=null) {
			m = PropertyUtils.getWriteMethod(pd);
		}
		if (m==null) { //validate if the attribute exists
			addLocalWarning("does not have an attribute ["+name+"] to set to value ["+value+"]");
		} else if(AnnotationUtils.findAnnotation(m, ProtectedAttribute.class) != null) {
			addLocalWarning("attribute ["+name+"] is protected, cannot be set from configuration");
		} else {
			checkDeprecationAndConfigurationWarning(name, m); //check if the setter has been deprecated

			if(value.contains(StringResolver.DELIM_START) && value.contains(StringResolver.DELIM_STOP)) { //If value contains a property, resolve it
				value = resolveValue(value);
			} else { //Only check for default values for non-property values
				checkTypeCompatibility(pd, name, value, attributes);
			}

			Object valueToSet = parseValueToSet(m, value);
			log.trace("attempting to populate field [{}] with value [{}] on object [{}]", ()->name, ()->valueToSet, ()->getBean());

			if(valueToSet != null) {
				try {
					BeanUtils.setProperty(getBean(), name, valueToSet);
				} catch (InvocationTargetException e) {
					log.warn("unable to populate field [{}] with value [{}] on object [{}]", name, valueToSet, getBean(), e);
					addLocalWarning(e.getCause().getMessage());
				}
			}
		}
	}

	private Object parseValueToSet(Method m, String value) {
		Class<?> setterArgumentClass = m.getParameters()[0].getType();
		//Try to parse the value as an Enum
		if(setterArgumentClass.isEnum()) {
			return parseAsEnum(setterArgumentClass, value);
		}

		return value;
	}

	/**
	 * Attempt to parse the attributes value as an Enum.
	 * @param enumClass The Enum class used to parse the value
	 * @param value The value to be parsed
	 * @return The Enum constant or <code>NULL</code> (and a local configuration warning) if it cannot parse the value.
	 */
	@SuppressWarnings("unchecked")
	private <E extends Enum<E>> E parseAsEnum(Class<?> enumClass, String value) {
		try {
			return EnumUtils.parse((Class<E>) enumClass, value);
		} catch(IllegalArgumentException e) {
			addLocalWarning(e.getMessage());
			return null;
		}
	}

	/**
	 * Check if the value is not empty,
	 * Can be parsed to match the Getters return type, 
	 * Equals the default value (parsed by invoking the getter, if present).
	 * If no Getter is present, tries to match the type to the Setters first argument.
	 */
	private void checkTypeCompatibility(PropertyDescriptor pd, String name, String value, Map<String, String> attrs) {
		Method rm = PropertyUtils.getReadMethod(pd);
		if (rm != null) {
			try {
				Object bean = getBean();
				Object defaultValue = rm.invoke(bean, new Object[0]);
				if (bean instanceof HasSpecialDefaultValues) {
					defaultValue = ((HasSpecialDefaultValues)bean).getSpecialDefaultValue(name, defaultValue, attrs);
				}
				if (defaultValue!=null) {
					if (value.length()==0) {
						addLocalWarning("attribute ["+name+"] has no value");
					} else if (equals(defaultValue, value)) {
						addLocalWarning("attribute ["+name+"] already has a default value ["+value+"]");
					}
				}
			} catch (NumberFormatException e) {
				addLocalWarning("attribute ["+ name+"] with value ["+value+"] cannot be converted to a number: "+e.getMessage());
			} catch (Throwable t) {
				addLocalWarning("is unable to parse attribute ["+name+"] value ["+value+"] to method ["+rm.getName()+"] with type ["+rm.getReturnType()+"]");
				log.warn("Error on getting default for object [" + getObjectName() + "] with method [" + rm.getName() + "] attribute ["+name+"] value ["+value+"]", t);
			}
		} else {
			//No readMethod, thus we cannot check the default value. We can however check if we can parse the value
			if (value.length()==0) {
				addLocalWarning("attribute ["+name+"] has no value");
				return;
			}

			//If it's a number (int/long) try to parse it, else BeanUtils will call the method with 0.
			try {
				Class<?> setterArgumentClass = pd.getWriteMethod().getParameters()[0].getType();

				switch (setterArgumentClass.getTypeName()) {
				case "int":
				case "java.lang.Integer":
					Integer.parseInt(value);
					break;
				case "long":
					Long.parseLong(value);
					break;
				}
			} catch(NumberFormatException e) {
				addLocalWarning("attribute ["+name+"] with value ["+value+"] cannot be converted to a number");
			} catch(Exception e) {
				log.debug("unable to get the first setter parameter of attribute["+name+"] writeMethod ["+pd.getWriteMethod()+"]", e);
			}
		}
	}

	/**
	 * Fancy equals that type-checks one value against another.
	 */
	private boolean equals(Object defaultValue, String value) {
		return	(defaultValue instanceof String && value.equals(defaultValue)) ||
				(defaultValue instanceof Boolean && Boolean.valueOf(value).equals(defaultValue)) ||
				(defaultValue instanceof Integer && Integer.valueOf(value).equals(defaultValue)) ||
				(defaultValue instanceof Long && Long.valueOf(value).equals(defaultValue));
	}

	private void checkDeprecationAndConfigurationWarning(String name, Method m) {
		ConfigurationWarning warning = AnnotationUtils.findAnnotation(m, ConfigurationWarning.class);
		if(warning != null) {
			String msg = "attribute ["+name+"]";
			boolean isDeprecated = AnnotationUtils.findAnnotation(m, Deprecated.class) != null;

			if(isDeprecated) {
				msg += " is deprecated";
			}

			if(StringUtils.isNotEmpty(warning.value())) {
				msg += ": " + warning.value();
			}

			if(!(suppressDeprecationWarnings && isDeprecated)) { //Don't log if deprecation warnings are suppressed and it is deprecated
				addLocalWarning(msg);
			}
		}
	}
}
