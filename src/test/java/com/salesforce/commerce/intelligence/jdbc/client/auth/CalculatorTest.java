package com.salesforce.commerce.intelligence.jdbc.client.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CalculatorTest
{

    private final Calculator calculator = new Calculator();

    @Test
    public void testAddition() {
        assertEquals(5, calculator.add(2, 3));
    }

/*
    @Test
    public void testSubtraction() {
        assertEquals(1, calculator.subtract(4, 3));
    }

    @Test
    public void testMultiplication() {
        assertEquals(12, calculator.multiply(4, 3));
    }
*/

    @Test
    public void testDivision() {
        assertEquals(2, calculator.divide(6, 3));
    }

    @Test
    public void testDivisionByZero() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> calculator.divide(5, 0));
        assertEquals("Division by zero is not allowed", exception.getMessage());
    }
}
