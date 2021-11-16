package nl.nn.adapterframework.parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import nl.nn.adapterframework.core.ParameterException;

public class ParameterListTest {

	@Test
	public void testParameterList() throws ParameterException {
		ParameterList list = new ParameterList();

		Parameter key2 = new SimpleParameter("key2", "value2");
		list.add(new SimpleParameter("key1", "value1"));
		list.add(key2);
		list.add(new SimpleParameter("key4", "value4"));
		list.add(new SimpleParameter("key3", "value3"));

		assertTrue(list.contains("key1"));
		assertTrue(list.contains("key2"));
		assertFalse(list.contains("doesnt-exist"));
		assertEquals(4, list.size());

		List<String> sortedList2 = new ArrayList<>();
		for (Parameter param : list) {
			sortedList2.add(param.getName());
		}
		assertEquals("[key1, key2, key4, key3]", sortedList2.toString());

		assertSame(key2, list.remove("key2"));
		assertNull(list.remove("doesnt-exist"));

		assertEquals("value3", list.get("key3").getValue());
		assertEquals("value1", list.getParameter(0).getValue());
		assertEquals("value4", list.getParameter(1).getValue());
		assertEquals("value3", list.getParameter(2).getValue());
	}
}
