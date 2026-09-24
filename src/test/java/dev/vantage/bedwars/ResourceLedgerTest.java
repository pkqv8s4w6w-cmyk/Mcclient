package dev.vantage.bedwars;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLedgerTest {

    @Test
    void pickupsAddUpAndSeenPurchasesComeOff() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.collect("Steve", ResourceLedger.Resource.IRON, 32);
        ledger.collect("Steve", ResourceLedger.Resource.IRON, 16);
        ledger.spend("Steve", ResourceLedger.Purchase.STONE_SWORD, 1);
        assertEquals(38, ledger.balance("Steve", ResourceLedger.Resource.IRON));
        assertEquals(48, ledger.collectedTotal("Steve", ResourceLedger.Resource.IRON));
    }

    @Test
    void theBalanceNeverGoesNegative() {
        ResourceLedger ledger = new ResourceLedger();
        // Seen wearing diamond armour without being seen collecting: they had it before we looked.
        ledger.spend("Alex", ResourceLedger.Purchase.DIAMOND_ARMOUR, 1);
        assertEquals(0, ledger.balance("Alex", ResourceLedger.Resource.EMERALD));
    }

    @Test
    void affordableThreatsListTheDangerousOnesFirst() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.collect("Rich", ResourceLedger.Resource.EMERALD, 7);
        ledger.collect("Rich", ResourceLedger.Resource.IRON, 45);
        List<ResourceLedger.Purchase> threats = ledger.affordableThreats("Rich");
        assertEquals(ResourceLedger.Purchase.ENDER_PEARL, threats.get(0));
        assertTrue(threats.contains(ResourceLedger.Purchase.DIAMOND_ARMOUR));
        assertTrue(threats.contains(ResourceLedger.Purchase.FIREBALL));
        assertFalse(threats.contains(ResourceLedger.Purchase.TNT));
    }

    @Test
    void playersAreKeptApart() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.collect("One", ResourceLedger.Resource.GOLD, 10);
        assertEquals(0, ledger.balance("Two", ResourceLedger.Resource.GOLD));
    }
}
