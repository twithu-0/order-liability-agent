package com.twithu.orderliabilityagent.constants;

import java.util.Set;

public class OrderConstant {

    public static final Set<String> CHANNEL_SET = Set.of(
            "携程", "去哪儿", "飞猪",  "大众点评", "Booking","同程"
    );

    public static final Set<String> SUPPLIER_SET = Set.of(
            "美团", "艺龙","Agoda","Hotelbeds","Expedia"
    );
}
