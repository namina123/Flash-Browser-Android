package com.oxgames.rufflewrapper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.oxgames.rufflewrapper.amf.Amf0Body;
import com.oxgames.rufflewrapper.amf.Amf0Message;
import com.oxgames.rufflewrapper.amf.Amf3Object;
import com.oxgames.rufflewrapper.amf.AmfArray;
import com.oxgames.rufflewrapper.amf.AmfCodec;
import com.oxgames.rufflewrapper.amf.ArrayCollection;
import com.oxgames.rufflewrapper.amf.AsObject;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class AmfCodecTest {

    @Test
    public void dutyRewardRequestMatchesPyamfEnvelope() throws Exception {
        byte[] actual = PvzolAmfClient.buildDutyRewardRequestPayload(1, 3);
        byte[] expected = new byte[] {
                0x00, 0x03, 0x00, 0x00, 0x00, 0x01,
                0x00, 0x0f, 0x61, 0x70, 0x69, 0x2e, 0x64, 0x75, 0x74, 0x79, 0x2e, 0x72, 0x65, 0x77, 0x61, 0x72, 0x64,
                0x00, 0x02, 0x2f, 0x31,
                0x00, 0x00, 0x00, 0x00,
                0x0a, 0x00, 0x00, 0x00, 0x02,
                0x11, 0x04, 0x01,
                0x11, 0x04, 0x03
        };
        assertArrayEquals(expected, actual);
    }

    @Test
    public void amf3RoundTripsTypedDynamicObject() throws Exception {
        AsObject input = new AsObject("pvz.User");
        input.put("name", "Alice");
        input.put("score", Integer.valueOf(42));
        input.put("createdAt", new Date(1700000000000L));
        input.put("bytes", new byte[] {1, 2, 3});

        AmfArray inventory = new AmfArray();
        inventory.add("seed");
        inventory.add("water");
        inventory.getAssociativeValues().put("slot", Integer.valueOf(3));
        input.put("inventory", inventory);

        Object decoded = AmfCodec.decodeAmf3(AmfCodec.encodeAmf3(input));
        assertTrue(decoded instanceof AsObject);

        AsObject result = (AsObject) decoded;
        assertEquals("pvz.User", result.getType());
        assertEquals("Alice", result.get("name"));
        assertEquals(Integer.valueOf(42), result.get("score"));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) result.get("bytes"));
        assertTrue(result.get("inventory") instanceof AmfArray);
        AmfArray decodedInventory = (AmfArray) result.get("inventory");
        assertEquals("seed", decodedInventory.get(0));
        assertEquals(Integer.valueOf(3), decodedInventory.getAssociativeValues().get("slot"));
    }

    @Test
    public void amf3RoundTripsArrayCollection() throws Exception {
        ArrayCollection input = new ArrayCollection(Arrays.asList("a", Integer.valueOf(2), Boolean.TRUE));
        Object decoded = AmfCodec.decodeAmf3(AmfCodec.encodeAmf3(input));
        assertTrue(decoded instanceof ArrayCollection);
        assertEquals(input, decoded);
    }

    @Test
    public void amf0RoundTripsEnvelopeWithAmf3Body() throws Exception {
        AsObject payload = new AsObject("pvz.Session");
        payload.put("token", "abc");

        Amf0Message message = new Amf0Message();
        message.addHeader("Credentials", false, "cookie=value");
        message.addBody(new Amf0Body("service.method", "/1", new Amf3Object(payload), Amf0Body.DATA_TYPE_AMF3_OBJECT));

        Amf0Message decoded = AmfCodec.decodeAmf0Message(AmfCodec.encodeAmf0Message(message));
        assertEquals(1, decoded.getHeaders().size());
        assertEquals("cookie=value", decoded.getHeaders().get(0).getValue());
        assertEquals(1, decoded.getBodies().size());
        assertTrue(decoded.getBodies().get(0).getValue() instanceof Amf3Object);
        Object bodyValue = ((Amf3Object) decoded.getBodies().get(0).getValue()).getValue();
        assertTrue(bodyValue instanceof AsObject);
        assertEquals("pvz.Session", ((AsObject) bodyValue).getType());
        assertEquals("abc", ((AsObject) bodyValue).get("token"));
    }

    @Test
    public void dutyTaskPlanUsesMainTaskRangesAndFixedCategoryThree() {
        AsObject root = new AsObject();
        root.put("mainTask", Arrays.asList(taskWithId(7), taskWithId(12)));
        root.put("sideTask", Arrays.asList(
                taskWithId(21), taskWithId(24), taskWithId(28), taskWithId(35), taskWithId(43),
                taskWithId(45), taskWithId(47), taskWithId(49), taskWithId(51), taskWithId(53),
                taskWithId(55), taskWithId(57), taskWithId(59), taskWithId(61), taskWithId(62),
                taskWithId(65), taskWithId(70)
        ));

        PvzolAmfClient.DutyTaskPlan plan = PvzolAmfClient.planDutyRewardRequests(root);

        assertTrue(plan.hasMainTask);
        assertEquals(7, plan.rewardRequests.size());
        assertReward(plan.rewardRequests.get(0), 1, 3);
        assertReward(plan.rewardRequests.get(1), 2, 3);
        assertReward(plan.rewardRequests.get(2), 3, 3);
        assertReward(plan.rewardRequests.get(3), 4, 3);
        assertReward(plan.rewardRequests.get(4), 5, 3);
        assertReward(plan.rewardRequests.get(5), 6, 3);
        assertReward(plan.rewardRequests.get(6), 12, 3);
    }

    @Test
    public void dutyTaskPlanExpandsMissingSideTaskRangeAndSkipsFoundSingleRange() {
        AsObject root = new AsObject();
        root.put("mainTask", Arrays.asList(taskWithId(1), taskWithId(12)));
        root.put("sideTask", Arrays.asList(
                new AsObject(), taskWithId(24), taskWithId(28), taskWithId(35), taskWithId(43),
                taskWithId(45), taskWithId(47), taskWithId(49), taskWithId(51), taskWithId(53),
                taskWithId(55), taskWithId(57), taskWithId(59), taskWithId(61), taskWithId(62),
                taskWithId(65), taskWithId(70)
        ));

        PvzolAmfClient.DutyTaskPlan plan = PvzolAmfClient.planDutyRewardRequests(root);
        List<Integer> rewardIds = new java.util.ArrayList<>();
        for (PvzolAmfClient.RewardRequest rewardRequest : plan.rewardRequests) {
            rewardIds.add(Integer.valueOf(rewardRequest.rewardId));
        }

        assertTrue(rewardIds.contains(Integer.valueOf(1)));
        assertTrue(rewardIds.contains(Integer.valueOf(12)));
        assertTrue(rewardIds.contains(Integer.valueOf(21)));
        assertTrue(rewardIds.contains(Integer.valueOf(22)));
        assertTrue(rewardIds.contains(Integer.valueOf(23)));
        assertFalse(rewardIds.contains(Integer.valueOf(61)));
    }

    @Test
    public void dutyTaskPlanRequiresMainTask() {
        AsObject root = new AsObject();
        root.put("sideTask", Arrays.asList(taskWithId(21)));

        PvzolAmfClient.DutyTaskPlan plan = PvzolAmfClient.planDutyRewardRequests(root);

        assertFalse(plan.hasMainTask);
        assertTrue(plan.rewardRequests.isEmpty());
    }

    private static AsObject taskWithId(int id) {
        AsObject task = new AsObject();
        task.put("id", Integer.valueOf(id));
        return task;
    }

    private static void assertReward(PvzolAmfClient.RewardRequest rewardRequest, int rewardId, int category) {
        assertEquals(rewardId, rewardRequest.rewardId);
        assertEquals(category, rewardRequest.category);
    }
}
