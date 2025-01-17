/*
   Copyright 2013, 2016 Nationale-Nederlanden, 2020-2021 WeAreFrank!

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
package nl.nn.adapterframework.senders;

import java.util.Collections;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import lombok.Getter;
import lombok.Setter;
import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.configuration.ConfigurationWarning;
import nl.nn.adapterframework.core.IForwardTarget;
import nl.nn.adapterframework.core.PipeLineSession;
import nl.nn.adapterframework.core.PipeRunResult;
import nl.nn.adapterframework.core.SenderException;
import nl.nn.adapterframework.doc.IbisDoc;
import nl.nn.adapterframework.jta.IThreadConnectableTransactionManager;
import nl.nn.adapterframework.parameters.Parameter;
import nl.nn.adapterframework.parameters.Parameter.ParameterType;
import nl.nn.adapterframework.parameters.ParameterList;
import nl.nn.adapterframework.parameters.ParameterValueList;
import nl.nn.adapterframework.stream.IThreadCreator;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.stream.MessageOutputStream;
import nl.nn.adapterframework.stream.StreamingException;
import nl.nn.adapterframework.stream.StreamingSenderBase;
import nl.nn.adapterframework.stream.ThreadConnector;
import nl.nn.adapterframework.stream.ThreadLifeCycleEventListener;
import nl.nn.adapterframework.stream.xml.XmlTap;
import nl.nn.adapterframework.util.AppConstants;
import nl.nn.adapterframework.util.EnumUtils;
import nl.nn.adapterframework.util.TransformerPool;
import nl.nn.adapterframework.util.TransformerPool.OutputType;
import nl.nn.adapterframework.util.XmlUtils;
import nl.nn.adapterframework.xml.NamespaceRemovingFilter;
import nl.nn.adapterframework.xml.PrettyPrintFilter;
import nl.nn.adapterframework.xml.SkipEmptyTagsFilter;
import nl.nn.adapterframework.xml.TransformerFilter;
import nl.nn.adapterframework.xml.XmlWriter;

/**
 * Perform an XSLT transformation with a specified stylesheet or XPath-expression.
 *
 * @ff.parameters any parameters defined on the sender will be applied to the created transformer
 * 
 * @author  Gerrit van Brakel
 * @since   4.9
 */
public class XsltSender extends StreamingSenderBase implements IThreadCreator {

	public final OutputType DEFAULT_OUTPUT_METHOD=OutputType.XML;
	public final OutputType DEFAULT_XPATH_OUTPUT_METHOD=OutputType.TEXT;
	public final boolean DEFAULT_INDENT=false; // some existing ibises expect default for indent to be false 
	public final boolean DEFAULT_OMIT_XML_DECLARATION=false; 
	
	private @Getter String styleSheetName;
	private @Getter String styleSheetNameSessionKey=null;
	private @Getter String xpathExpression=null;
	private @Getter String namespaceDefs = null; 
	private @Getter OutputType outputType=null;
	private @Getter Boolean omitXmlDeclaration;
	private @Getter Boolean indentXml=null; 
	private @Getter boolean removeNamespaces=false;
	private @Getter boolean skipEmptyTags=false;
	private @Getter int xsltVersion=0; // set to 0 for auto detect.
	private @Getter boolean namespaceAware=XmlUtils.isNamespaceAwareByDefault();
	private @Getter boolean debugInput = false;
	
	private TransformerPool transformerPool;
	
	private Map<String, TransformerPool> dynamicTransformerPoolMap;
	private int transformerPoolMapSize = 100;

	protected ThreadLifeCycleEventListener<Object> threadLifeCycleEventListener;
	protected @Setter IThreadConnectableTransactionManager txManager;
	private @Getter boolean streamingXslt;


	/**
	 * The <code>configure()</code> method instantiates a transformer for the specified
	 * XSL. If the stylesheetName cannot be accessed, a ConfigurationException is thrown.
	 */
	@Override
	public void configure() throws ConfigurationException {
		super.configure();
		
		streamingXslt = AppConstants.getInstance(getConfigurationClassLoader()).getBoolean(XmlUtils.XSLT_STREAMING_BY_DEFAULT_KEY, false);
		dynamicTransformerPoolMap = Collections.synchronizedMap(new LRUMap(transformerPoolMapSize));
		
		if(StringUtils.isNotEmpty(getXpathExpression()) && getOutputType()==null) {
			setOutputType(DEFAULT_XPATH_OUTPUT_METHOD);
		}
		if(StringUtils.isNotEmpty(getStyleSheetName()) || StringUtils.isNotEmpty(getXpathExpression())) {
			Boolean omitXmlDeclaration = getOmitXmlDeclaration();
			if (omitXmlDeclaration==null) {
				omitXmlDeclaration=true;
			}
			transformerPool = TransformerPool.configureTransformer0(getLogPrefix(), this, getNamespaceDefs(), getXpathExpression(), getStyleSheetName(), getOutputType(), !omitXmlDeclaration, getParameterList(), getXsltVersion());
		}
		else if(StringUtils.isEmpty(getStyleSheetNameSessionKey())) {
			throw new ConfigurationException(getLogPrefix()+" one of xpathExpression, styleSheetName or styleSheetNameSessionKey must be specified");
		}

		if (getXsltVersion()>=2) {
			ParameterList parameterList = getParameterList();
			if (parameterList!=null) {
				for (int i=0; i<parameterList.size(); i++) {
					Parameter parameter = parameterList.getParameter(i);
					if (parameter.getType()==ParameterType.NODE) {
						throw new ConfigurationException(getLogPrefix() + "type '"+ParameterType.NODE+" is not permitted in combination with XSLT 2.0, use type '"+ParameterType.DOMDOC+"'");
					}
				}
			}
		}
	}

	@Override
	public void open() throws SenderException {
		super.open();

		if (transformerPool!=null) {
			try {
				transformerPool.open();
			} catch (Exception e) {
				throw new SenderException(getLogPrefix()+"cannot start TransformerPool", e);
			}
		}
	}

	@Override
	public void close() throws SenderException {
		super.close();
		
		if (transformerPool!=null) {
			transformerPool.close();
		}
		
		if (dynamicTransformerPoolMap!=null && !dynamicTransformerPoolMap.isEmpty()) {
			for(TransformerPool tp : dynamicTransformerPoolMap.values()) {
				tp.close();
			}
		}
	}


	protected ContentHandler filterInput(ContentHandler input, PipeLineSession session) {
		if (isRemoveNamespaces()) {
			log.debug(getLogPrefix()+ " providing filter to remove namespaces from input message");
			return new NamespaceRemovingFilter(input);
		}
		return input; // TODO might be necessary to do something about namespaceaware
	}
	
	
	@Override
	public MessageOutputStream provideOutputStream(PipeLineSession session, IForwardTarget next) throws StreamingException {
		ThreadConnector threadConnector = streamingXslt ? new ThreadConnector(this, threadLifeCycleEventListener, txManager,  session) : null; 
		MessageOutputStream target = MessageOutputStream.getTargetStream(this, session, next);
		ContentHandler handler = createHandler(null, threadConnector, session, target);
		return new MessageOutputStream(this, handler, target, threadLifeCycleEventListener, txManager, session, threadConnector);
	}

	protected ContentHandler createHandler(Message input, ThreadConnector threadConnector, PipeLineSession session, MessageOutputStream target) throws StreamingException {
		ContentHandler handler = null;

		try {
			ParameterValueList pvl=null;
			if (paramList!=null) {
				pvl = paramList.getValues(input, session);
			}

			TransformerPool poolToUse = transformerPool;
			if(StringUtils.isNotEmpty(styleSheetNameSessionKey)) {
				Message styleSheetNameToUse = session.getMessage(styleSheetNameSessionKey);
				if (!Message.isEmpty(styleSheetNameToUse )) {
					String styleSheetNameFromSessionKey = styleSheetNameToUse.asString();
					if(!dynamicTransformerPoolMap.containsKey(styleSheetNameFromSessionKey)) {
						dynamicTransformerPoolMap.put(styleSheetNameFromSessionKey, poolToUse = TransformerPool.configureTransformer(getLogPrefix(), this, null, null, styleSheetNameFromSessionKey, null, true, getParameterList()));
						poolToUse.open();
					} else {
						poolToUse = dynamicTransformerPoolMap.get(styleSheetNameFromSessionKey);
					}
				}
				if (poolToUse == null) {
					throw new SenderException("no XSLT stylesheet found from styleSheetNameSessionKey ["+styleSheetNameSessionKey+"], and neither one statically configured");
				}
			}
			
			OutputType outputType = getOutputType();
			if (log.isTraceEnabled()) log.trace("Configured outputmethod ["+outputType+"]");
			if (outputType == null) {
				String parsedOutputType = poolToUse.getOutputMethod();
				if (StringUtils.isNotEmpty(parsedOutputType)) {
					outputType = EnumUtils.parse(OutputType.class, parsedOutputType);
				}
				if (log.isTraceEnabled()) log.trace("Detected outputmethod ["+parsedOutputType+"]");
			}
			if (outputType == null) {
				outputType = DEFAULT_OUTPUT_METHOD;
				if (log.isTraceEnabled()) log.trace("Default outputmethod ["+outputType+"]");
			}

			Object targetStream = target.asNative();
			
			Boolean indentXml = getIndentXml();
			if (log.isTraceEnabled()) log.trace("Configured indentXml ["+indentXml+"]");
			if (indentXml==null) {
				indentXml = poolToUse.getIndent();
				if (log.isTraceEnabled()) log.trace("Detected indentXml ["+indentXml+"]");
			}
			if (indentXml==null) {
				indentXml = DEFAULT_INDENT;
				if (log.isTraceEnabled()) log.trace("Default indentXml ["+indentXml+"]");
			}
			
			Boolean omitXmlDeclaration = getOmitXmlDeclaration();
			if (targetStream instanceof ContentHandler) {
				handler = (ContentHandler)targetStream;
			} else {
				XmlWriter xmlWriter = new XmlWriter(target.asWriter());
				if (log.isTraceEnabled()) log.trace("Configured omitXmlDeclaration ["+omitXmlDeclaration+"]");
				if (outputType == OutputType.XML) {
					if (omitXmlDeclaration==null) {
						omitXmlDeclaration = poolToUse.getOmitXmlDeclaration();
						if (log.isTraceEnabled()) log.trace("Detected omitXmlDeclaration ["+omitXmlDeclaration+"]");
						if (omitXmlDeclaration==null) {
							omitXmlDeclaration=DEFAULT_OMIT_XML_DECLARATION;
							if (log.isTraceEnabled()) log.trace("Default omitXmlDeclaration ["+omitXmlDeclaration+"]");
						}
					}
					xmlWriter.setIncludeXmlDeclaration(!omitXmlDeclaration);
					if (indentXml) {
						xmlWriter.setNewlineAfterXmlDeclaration(true);
					}
				} else {
					xmlWriter.setTextMode(true);
				}
				handler = xmlWriter;
			}

			if (indentXml) {
				handler = new PrettyPrintFilter(handler);
			}
			if (isSkipEmptyTags()) {
				handler = new SkipEmptyTagsFilter(handler);
			}
			

			TransformerFilter mainFilter = poolToUse.getTransformerFilter(threadConnector, handler);
			if (pvl!=null) {
				XmlUtils.setTransformerParameters(mainFilter.getTransformer(), pvl.getValueMap());
			}
			handler=filterInput(mainFilter, session);
			
			return handler;
		} catch (Exception e) {
			//log.warn(getLogPrefix()+"intermediate exception logging",e);
			throw new StreamingException(getLogPrefix()+"Exception on creating transformerHandler chain", e);
		} 
	}
	

	protected XMLReader getXmlReader(PipeLineSession session, ContentHandler handler) throws ParserConfigurationException, SAXException {
		return XmlUtils.getXMLReader(handler);
	}
	

	/*
	 * alternative implementation of send message, that should do the same as the original, but reuses the streaming content handler
	 */
	@Override
	public PipeRunResult sendMessage(Message message, PipeLineSession session, IForwardTarget next) throws SenderException {
		if (message==null) {
			throw new SenderException(getLogPrefix()+"got null input");
		}
		try {
			try (ThreadConnector threadConnector = streamingXslt ? new ThreadConnector(this, threadLifeCycleEventListener, txManager, session) : null) {
				try (MessageOutputStream target=MessageOutputStream.getTargetStream(this, session, next)) {
					ContentHandler handler = createHandler(message, threadConnector, session, target);
					if (isDebugInput() && log.isDebugEnabled()) {
						handler = new XmlTap(handler) {
							@Override
							public void endDocument() throws SAXException {
								super.endDocument();
								log.debug(getLogPrefix()+" xml input ["+getWriter()+"]");
							}
						};
					}
					XMLReader reader = getXmlReader(session, handler);
					InputSource source = message.asInputSource();
					reader.parse(source);
					return target.getPipeRunResult();
				}
			}
		} catch (Exception e) {
			throw new SenderException(getLogPrefix()+"Exception on transforming input", e);
		}
	}
	

	@Override
	public boolean isSynchronous() {
		return true;
	}

	@IbisDoc({"1", "Location of stylesheet to apply to the input message", ""})
	public void setStyleSheetName(String stylesheetName){
		this.styleSheetName=stylesheetName;
	}

	@IbisDoc({"2", "Session key to retrieve stylesheet location. Overrides stylesheetName or xpathExpression attribute", ""})
	public void setStyleSheetNameSessionKey(String newSessionKey) {
		styleSheetNameSessionKey = newSessionKey;
	}

	@IbisDoc({"3", "Size of cache of stylesheets retrieved from styleSheetNameSessionKey", "100"})
	public void setStyleSheetCacheSize(int size) {
		transformerPoolMapSize = size;
	}
	
	@IbisDoc({"4", "Alternatively: xpath-expression to create stylesheet from", ""})
	public void setXpathExpression(String string) {
		xpathExpression = string;
	}

	@IbisDoc({"5", "omit the xml declaration on top of the output. When not set, the value specified in the stylesheet is followed", "false, if not set in stylesheet"})
	public void setOmitXmlDeclaration(Boolean b) {
		omitXmlDeclaration = b;
	}

	@IbisDoc({"6", "Namespace defintions for xpathExpression. Must be in the form of a comma or space separated list of <code>prefix=namespaceuri</code>-definitions. For some use other cases (NOT xpathExpression), one entry can be without a prefix, that will define the default namespace.", ""})
	public void setNamespaceDefs(String namespaceDefs) {
		this.namespaceDefs = namespaceDefs;
	}

	@IbisDoc({"7", "For xpathExpression only", "text"})
	public void setOutputType(OutputType string) {
		outputType = string;
	}

	@IbisDoc({"8", "when set <code>true</code>, result is pretty-printed. When not set, the value specified in the stylesheet is followed", "false, if not set in stylesheet"})
	public void setIndentXml(Boolean b) {
		indentXml = b;
	}

	@IbisDoc({"9", "when set <code>true</code> namespaces (and prefixes) in the input message are removed before transformation", "false"})
	public void setRemoveNamespaces(boolean b) {
		removeNamespaces = b;
	}

	@IbisDoc({"10", "when set <code>true</code> empty tags in the output are removed after transformation", "false"})
	public void setSkipEmptyTags(boolean b) {
		skipEmptyTags = b;
	}

	@IbisDoc({"11", "when set to <code>2</code> xslt processor 2.0 (net.sf.saxon) will be used, otherwise xslt processor 1.0 (org.apache.xalan). <code>0</code> will auto detect", "0"})
	public void setXsltVersion(int xsltVersion) {
		this.xsltVersion=xsltVersion;
	}

	@IbisDoc({"12", "", "true"})
	public void setNamespaceAware(boolean b) {
		namespaceAware = b;
	}

	@IbisDoc({"13", "when set <code>true</code> the input is written to the log file, at DEBUG level", "false"})
	public void setDebugInput(boolean debugInput) {
		this.debugInput = debugInput;
	}

	@IbisDoc({"14", "when set <code>true</code> xslt processor 2.0 (net.sf.saxon) will be used, otherwise xslt processor 1.0 (org.apache.xalan)", "false"})
	/**
	 * @deprecated Please remove setting of xslt2, it will be auto detected. Or use xsltVersion.
	 */
	@Deprecated
	@ConfigurationWarning("It's value is now auto detected. If necessary, replace with a setting of xsltVersion")
	public void setXslt2(boolean b) {
		xsltVersion=b?2:1;
	}

	@Override
	public void setThreadLifeCycleEventListener(ThreadLifeCycleEventListener<Object> threadLifeCycleEventListener) {
		this.threadLifeCycleEventListener=threadLifeCycleEventListener;
	}

}
