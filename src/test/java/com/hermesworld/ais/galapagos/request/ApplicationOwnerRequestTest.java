package com.hermesworld.ais.galapagos.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.util.JsonUtil;
import org.junit.jupiter.api.Test;

class ApplicationOwnerRequestTest {

    @Test
    void testApplicationOwnerRequestSerializable() throws Exception {
        ObjectMapper mapper = JsonUtil.newObjectMapper();

        ApplicationOwnerRequest request = new ApplicationOwnerRequest();
        request.setApplicationId("{strange\" ÄpplicationId!");
        request.setUserName("User");
        request.setState(RequestState.APPROVED);
        request.setComments("Very long string\nwith windows newlines\r\n");

        String json = mapper.writeValueAsString(request);

        request = mapper.readValue(json, ApplicationOwnerRequest.class);
        assertEquals("{strange\" ÄpplicationId!", request.getApplicationId());
        assertEquals("User", request.getUserName());
        assertTrue(request.getState() == RequestState.APPROVED);
        assertEquals("Very long string\nwith windows newlines\r\n", request.getComments());
    }

}
