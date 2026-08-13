package mx.edu.utch.melo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SucursalTest {

    @Test
    void calcularCostoEnvioSumaTarifaBaseMasTarifaPorKmPorDistancia() {
        Sucursal sucursal = new Sucursal();
        sucursal.setTarifaBaseEnvio(new BigDecimal("20.00"));
        sucursal.setTarifaPorKm(new BigDecimal("8.00"));

        BigDecimal costo = sucursal.calcularCostoEnvio(new BigDecimal("4.2"));

        // 20.00 + 8.00 * 4.2 = 53.60
        assertEquals(0, new BigDecimal("53.60").compareTo(costo));
    }

    @Test
    void calcularCostoEnvioEsCeroSinDistancia() {
        Sucursal sucursal = new Sucursal();
        sucursal.setTarifaBaseEnvio(new BigDecimal("20.00"));
        sucursal.setTarifaPorKm(new BigDecimal("8.00"));

        assertEquals(0, BigDecimal.ZERO.compareTo(sucursal.calcularCostoEnvio(null)));
    }

    @Test
    void calcularCostoEnvioEsCeroSiLaSucursalNoTieneTarifasConfiguradasTodavia() {
        // sucursal nueva sin capturar tarifas -- no debe bloquear el flujo (ver CLAUDE.md, Domicilios).
        Sucursal sucursal = new Sucursal();

        assertEquals(0, BigDecimal.ZERO.compareTo(sucursal.calcularCostoEnvio(new BigDecimal("4.2"))));
    }
}
