package com.icentric.Icentric.simulation.service;

import com.icentric.Icentric.simulation.entity.SimulationChoice;
import com.icentric.Icentric.simulation.service.impl.SimulationScoringServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationScoringServiceImplTest {

    private final SimulationScoringServiceImpl service = new SimulationScoringServiceImpl();

    @Test
    void capsScoreWithinRange() {
        SimulationChoice c1 = new SimulationChoice();
        c1.setCorrectnessScore(110);
        assertEquals(100, service.calculateChoiceScore(c1));

        SimulationChoice c2 = new SimulationChoice();
        c2.setCorrectnessScore(-5);
        assertEquals(0, service.calculateChoiceScore(c2));
    }

    @Test
    void returnsRawScoreForValidInput() {
        SimulationChoice c = new SimulationChoice();
        c.setCorrectnessScore(85);
        assertEquals(85, service.calculateChoiceScore(c));
    }
}
