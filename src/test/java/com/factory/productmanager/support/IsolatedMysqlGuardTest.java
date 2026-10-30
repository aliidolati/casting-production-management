package com.factory.productmanager.support;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsolatedMysqlGuardTest {
    @Test void rejectsNonSyntheticNamesBeforeStartingAnyServer() {
        for (String name : new String[]{"production", "../test", "a_synthetic;DROP DATABASE x", "a`_synthetic"}) {
            assertThrows(IllegalArgumentException.class, () -> IsolatedMysql.url(name));
        }
    }
}
