package com.hermesworld.ais.galapagos.events;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.util.FutureUtil;

public class GalapagosEventManagerMock implements GalapagosEventManager {

	private List<InvocationOnMock> sinkInvocations = new ArrayList<>();

	@Override
	public GalapagosEventSink newEventSink(KafkaCluster kafkaCluster) {
		Answer<?> logCall = inv -> {
			sinkInvocations.add(inv);
			return FutureUtil.noop();
		};
		return mock(GalapagosEventSink.class, logCall);
	}

	public List<InvocationOnMock> getSinkInvocations() {
		return sinkInvocations;
	}

}
