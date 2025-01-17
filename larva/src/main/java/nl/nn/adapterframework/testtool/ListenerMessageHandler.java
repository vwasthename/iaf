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
package nl.nn.adapterframework.testtool;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.Logger;

import nl.nn.adapterframework.core.IListener;
import nl.nn.adapterframework.core.IMessageHandler;
import nl.nn.adapterframework.core.ListenerException;
import nl.nn.adapterframework.core.TimeOutException;
import nl.nn.adapterframework.stream.Message;
import nl.nn.adapterframework.util.LogUtil;

/**
 * Message handler for JavaListener and WebServiceListener.
 * 
 * @author Jaco de Groot
 * @author Niels Meijer
 */
public class ListenerMessageHandler<M> implements IMessageHandler<M> {
	private static Logger log = LogUtil.getLogger(ListenerMessageHandler.class);
	private final BlockingQueue<ListenerMessage> requestMessages = new ArrayBlockingQueue<>(100);
	private final BlockingQueue<ListenerMessage> responseMessages = new ArrayBlockingQueue<>(100);

	private long defaultTimeout = TestTool.globalTimeout;

	@Override
	public Message processRequest(IListener<M> origin, String correlationId, M rawMessage, Message message, Map<String, Object> context) throws ListenerException {
		try {
			ListenerMessage requestMessage = new ListenerMessage(correlationId, message.asString(), context);
			requestMessages.add(requestMessage);

			ListenerMessage responseMessage = getResponseMessage(defaultTimeout);
			return new Message(responseMessage.getMessage());
		} catch (IOException e) {
			throw new ListenerException("cannot convert message to string", e);
		} catch (TimeOutException e) {
			throw new ListenerException("error processing request", e);
		}
	}

	/** Attempt to retrieve a {@link ListenerMessage}. Returns NULL if none is present */
	public ListenerMessage getRequestMessage() {
		try {
			return getRequestMessage(0);
		} catch (TimeOutException e) {
			return null;
		}
	}
	/** Attempt to retrieve a {@link ListenerMessage} with timeout in ms. Returns TimeOutException if non is present */
	public ListenerMessage getRequestMessage(long timeout) throws TimeOutException {
		return getMessageFromQueue(requestMessages, timeout, "request");
	}

	private ListenerMessage getMessageFromQueue(BlockingQueue<ListenerMessage> queue, long timeout, String messageType) throws TimeOutException {
		try {
			ListenerMessage requestMessage = queue.poll(timeout, TimeUnit.MILLISECONDS);
			if(requestMessage != null) {
				return requestMessage;
			}
		} catch (InterruptedException e) {
			log.error("interrupted while waiting for "+messageType+" message", e);
			Thread.currentThread().interrupt();
		}

		throw new TimeOutException();
	}

	/** Attempt to retrieve a {@link ListenerMessage}. Returns NULL if none is present */
	public ListenerMessage getResponseMessage() {
		try {
			return getResponseMessage(0);
		} catch (TimeOutException e) {
			return null;
		}
	}
	/** Attempt to retrieve a {@link ListenerMessage} with timeout in ms. Returns TimeOutException if non is present */
	public ListenerMessage getResponseMessage(long timeout) throws TimeOutException {
		return getMessageFromQueue(responseMessages, timeout, "response");
	}

	public void putResponseMessage(ListenerMessage listenerMessage) {
		if (listenerMessage != null) {
			responseMessages.add(listenerMessage);
		} else {
			log.error("listenerMessage is null");
		}
	}

	public void setTimeout(long defaultTimeout) {
		this.defaultTimeout = defaultTimeout;
	}

	@Override
	public void processRawMessage(IListener<M> origin, M rawMessage) throws ListenerException {
		processRawMessage(origin, rawMessage, null, false);
	}

	@Override
	public void processRawMessage(IListener<M> origin, M rawMessage, Map<String, Object> threadContext, boolean duplicatesAlreadyChecked) throws ListenerException {
		processRawMessage(origin, rawMessage, threadContext, -1, duplicatesAlreadyChecked);
	}

	@Override
	public void processRawMessage(IListener<M> origin, M rawMessage, Map<String, Object> threadContext, long waitingTime, boolean duplicatesAlreadyChecked) throws ListenerException {
		String correlationId = origin.getIdFromRawMessage(rawMessage, threadContext);
		Message message = origin.extractMessage(rawMessage, threadContext);
		processRequest(origin, correlationId, rawMessage, message, threadContext);
	}


	@Override
	public Message formatException(String origin, String arg1, Message arg2, Throwable arg3) {
		NotImplementedException e = new NotImplementedException();
		log.error("formatException not implemented", e);
		return null;
	}

}
