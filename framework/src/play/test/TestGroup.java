package play.test;

import java.util.Map;
import java.util.HashMap;

public enum TestGroup {
    ONE(1), TWO(2), THREE(3), FOUR(4);

    private Integer number;
    private static Map<Integer, TestGroup> groupMap = new HashMap<Integer, TestGroup>();
    static {
        for (TestGroup group : values())
            groupMap.put(group.number, group);
    }

    TestGroup(Integer number) {
        this.number = number;
    }

    public static TestGroup get(Integer number) {
        if(number == null) {
            return null;
        }
        return groupMap.get(number);
    }
}