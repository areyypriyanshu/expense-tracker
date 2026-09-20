package com.expensetracker.services.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    @Test
    fun `prefers grand total over other labelled values`() {
        val result = ReceiptParser.parse(
            """
            TAX INVOICE
            Fresh Mart
            Subtotal ₹1,000.00
            Tax ₹180.00
            Grand Total ₹1,180.00
            """.trimIndent()
        )

        assertEquals(1180.0, result.totalAmount!!, 0.001)
        assertEquals("INR", result.currency)
    }

    @Test
    fun `extracts total and merchant from typical Indian receipt`() {
        val result = ReceiptParser.parse(
            """
            TAX INVOICE
            SAI KRUPA SUPERMARKET
            Date: 12/08/2025
            Rice 1,200.00
            Total Rs. 1,234.50
            Thank you
            """.trimIndent()
        )

        assertEquals(1234.50, result.totalAmount!!, 0.001)
        assertEquals("SAI KRUPA SUPERMARKET", result.merchant)
        assertEquals(LocalDate.of(2025, 8, 12), result.date!!.toLocalDate())
        assertEquals("INR", result.currency)
    }

    @Test
    fun `recognizes amount due and comma separated INR amount`() {
        val result = ReceiptParser.parse("Cafe Blue\nAmount Due: INR 1,234.50")

        assertEquals(1234.50, result.totalAmount!!, 0.001)
        assertEquals("INR", result.currency)
    }

    @Test
    fun `recognizes payable and net total in their priority order`() {
        val payable = ReceiptParser.parse("Metro Store\nPayable: ₹499.00")
        val netTotal = ReceiptParser.parse("Metro Store\nNet Total: Rs. 750")

        assertEquals(499.0, payable.totalAmount!!, 0.001)
        assertEquals(750.0, netTotal.totalAmount!!, 0.001)
    }

    @Test
    fun `does not choose an unlabelled largest number`() {
        val result = ReceiptParser.parse("Shop One\nItem A 99\nItem B 2,500\nTax 450")

        assertNull(result.totalAmount)
    }

    @Test
    fun `leaves ambiguous labelled totals empty`() {
        val result = ReceiptParser.parse("Store\nTotal: ₹450.00\nTotal: ₹550.00")

        assertNull(result.totalAmount)
    }

    @Test
    fun `extracts ISO and written receipt dates`() {
        val isoDate = ReceiptParser.parse("Corner Shop\nDate 2025-03-04\nTotal ₹10")
        val writtenDate = ReceiptParser.parse("Corner Shop\n04 Mar 2025\nTotal ₹10")

        assertEquals(LocalDate.of(2025, 3, 4), isoDate.date!!.toLocalDate())
        assertEquals(LocalDate.of(2025, 3, 4), writtenDate.date!!.toLocalDate())
    }

    @Test
    fun `handles noisy OCR without producing a false total`() {
        val result = ReceiptParser.parse("T4X 1NV01CE\n@@@ ##\n1lO O0\nGST1N: 27ABCDE")

        assertNull(result.totalAmount)
        assertTrue(result.categoryText.isNotEmpty())
    }

    @Test
    fun `does not infer generic total from qty tax discount or items totals`() {
        val result = ReceiptParser.parse(
            """
            Store
            Total Qty: 2
            Tax Total: 18
            Discount Total: 50
            Total Items: 4
            """.trimIndent()
        )

        assertNull(result.totalAmount)
    }

    @Test
    fun `supports Indian lakh and crore formats with symbol`() {
        val result = ReceiptParser.parse(
            """
            Super Mart
            Grand Total: ₹1,23,456.50
            """.trimIndent()
        )

        assertEquals(123456.50, result.totalAmount!!, 0.001)
        assertEquals("INR", result.currency)
    }

    @Test
    fun `extracts multiline totals correctly`() {
        val t1 = ReceiptParser.parse("Bhagini Sriganda Palace\nTotal: ₹\n3150")
        val t2 = ReceiptParser.parse("Bhagini Sriganda Palace\nTotal:\n3150")
        val t3 = ReceiptParser.parse("Bhagini Sriganda Palace\nGrand Total\n₹ 3,150")
        val t4 = ReceiptParser.parse("Bhagini Sriganda Palace\nAmount Due:\n₹\n3,150")

        assertEquals(3150.0, t1.totalAmount!!, 0.001)
        assertEquals(3150.0, t2.totalAmount!!, 0.001)
        assertEquals(3150.0, t3.totalAmount!!, 0.001)
        assertEquals(3150.0, t4.totalAmount!!, 0.001)
        assertEquals("Bhagini Sriganda Palace", t4.merchant)
    }

    @Test
    fun `does not associate unrelated tax or item lines with multiline total`() {
        val r1 = ReceiptParser.parse("Store\nTotal:\nTax: ₹75")
        val r2 = ReceiptParser.parse("Store\nTotal:\nItem 123\n₹50")

        assertNull(r1.totalAmount)
        assertNull(r2.totalAmount)
    }

    @Test
    fun `test case 1 bhagini restaurant`() {
        val result = ReceiptParser.parse(
            """
            Bhagini
            Sriganda Palace
            Date: 16 May 2024
            Sub-Total: ₹3000
            CGST: ₹75
            SGST: ₹75
            Mode: Cash
            Total: ₹
            3150
            """.trimIndent()
        )

        assertEquals("Bhagini Sriganda Palace", result.merchant)
        assertEquals(3150.0, result.totalAmount!!, 0.001)
        assertEquals(LocalDate.of(2024, 5, 16), result.date!!.toLocalDate())
        assertEquals("INR", result.currency)
    }

    @Test
    fun `test case 2 local diner prefers net amount over gross total`() {
        val result = ReceiptParser.parse(
            """
            The Local Diner
            Total Quantity 9.000
            Gross Total 2580.00
            VAT 5.5% 55.55
            VAT 14.5% 227.65
            Service Tax 5.6% 158.93
            Service Charges 10.00% 258.00
            Net Amount 3280.00
            """.trimIndent()
        )

        assertEquals(3280.0, result.totalAmount!!, 0.001)
    }

    @Test
    fun `test case 3 old pal dhaba`() {
        val result = ReceiptParser.parse(
            """
            Old Pal Dhaba
            Date: 23 May 2024
            Sub Total: ₹1015
            CGST: ₹0
            SGST: ₹0
            Total: ₹1015
            """.trimIndent()
        )

        assertEquals(1015.0, result.totalAmount!!, 0.001)
        assertEquals(LocalDate.of(2024, 5, 23), result.date!!.toLocalDate())
    }

    @Test
    fun `test case 4 sleek bill`() {
        val result = ReceiptParser.parse(
            """
            Sleek Bill
            Subtotal ₹900.00
            IGST at 0% 0.00
            IGST at 3% 3.00
            IGST at 5% 5.00
            IGST at 12% 60.00
            TOTAL ₹968.00
            """.trimIndent()
        )

        assertEquals(968.0, result.totalAmount!!, 0.001)
    }

    @Test
    fun `bhagini exact raw ocr structure rejects earlier generic total and accepts final total`() {
        val rawOcr = """
            Name: Siva Shankar
            Service Rd, T K Reddy Layout, Annaiah Reddy Layout,
            Banaswadi, Bengaluru, Karnataka 560043
            Table: #37
            Item
            Mutton biriyani
            Tandoori Roti
            Chilly chicken
            Bhagini
            Chicken pepper
            Sriganda Palace
            GST No 29ADDPR8125K1Z2
            RECEIPT
            Price
            400
            F30
            F250
            F250
            Qty
            5
            2
            3
            Sub-Total:
            CGST:
            SGST:
            Mode: Cash
            Invoice No: 7767
            Date: 16 May 2024
            Time: 21:18
            Total
            1600
            150
            F500
            750
            3000
            2.5% 75
            2.5% 75
            Total:
            3150
        """.trimIndent()

        val result = ReceiptParser.parse(rawOcr)
        assertEquals(3150.0, result.totalAmount!!, 0.001)
        // Real ML Kit output reorders columns - merchant may be partial
        assertEquals("Bhagini", result.merchant)
        assertEquals(LocalDate.of(2024, 5, 16), result.date!!.toLocalDate())
    }

    @Test
    fun `supports various currency prefix formats for lakh and crore amounts`() {
        val r1 = ReceiptParser.parse("Store\nTotal Rs. 1,23,456.50")
        val r2 = ReceiptParser.parse("Store\nTotal INR 1,23,456.50")
        val r3 = ReceiptParser.parse("Store\nTotal ₹ 1,234.50")

        assertEquals(123456.50, r1.totalAmount!!, 0.001)
        assertEquals("INR", r1.currency)
        assertEquals(123456.50, r2.totalAmount!!, 0.001)
        assertEquals("INR", r2.currency)
        assertEquals(1234.50, r3.totalAmount!!, 0.001)
        assertEquals("INR", r3.currency)
    }

    @Test
    fun `field independent extraction allows valid merchant with missing amount`() {
        val result = ReceiptParser.parse("Corner Bakery\nThank you for visiting\nNo total here")
        assertEquals("Corner Bakery", result.merchant)
        assertNull(result.totalAmount)
    }

    @Test
    fun `field independent extraction allows valid amount with missing merchant and date`() {
        val result = ReceiptParser.parse("Total: 968.00")
        assertEquals(968.0, result.totalAmount!!, 0.001)
        assertNull(result.merchant)
        assertNull(result.date)
    }

    @Test
    fun `defaults currency to INR when a receipt has only an unmarked total`() {
        val result = ReceiptParser.parse("Local Store\nTotal 1,234.50")

        assertEquals(1234.50, result.totalAmount!!, 0.001)
        assertEquals("INR", result.currency)
    }

    @Test
    fun `ignores lone dollar sign and defaults to base currency`() {
        val result1 = ReceiptParser.parse("Store\nTotal $9,474.00")
        assertEquals("INR", result1.currency)

        val result2 = ReceiptParser.parse("Store\n1$3,500/ea\nTotal 9474.00", "INR")
        assertEquals("INR", result2.currency)

        val result3 = ReceiptParser.parse("Store\nTotal 500.00", "USD")
        assertEquals("USD", result3.currency)
    }

    @Test
    fun `recognizes explicit USD currency`() {
        val r1 = ReceiptParser.parse("Store\nTotal USD 100.00")
        assertEquals("USD", r1.currency)

        val r2 = ReceiptParser.parse("Store\nTotal US$150.00")
        assertEquals("USD", r2.currency)
    }

    @Test
    fun `handles metro super store ocr layout`() {
        val ocr = """
            Bill No: INV92797
            ITEN
            YOUR NEIGHEOURHOOD S UPERMARKET
            19/07/2026, 08:17 AM
            Cashier: Priya
            Room Freshener
            METRO SUPER STORE
            1kg
            Energy Drink 250ml
            Masoor Dal 1kq
            Urad Dal 1kg
            Poha 500g
            chickpeas (Kabuli chana) 4
            MRP Total
            chembur, Mumbai
            Discount
            Incl. GST
            TOTAL
            9TY
            4
            3
            2
            3
            4
            RAIE
            R110.00
            108.00
            7114.00
            You saved 114. 00!
            20 items purchased
            126.00
            Payment Mode: Cash
            105.00
            (45.00
            Thank You for Shopping with Us
            Ctr: T-2
            *INV9 2797*
            ANI
            t432.0o
            t330.00
            (456.00
            t210.00
            t378.00
            t180.00
            t2100. 00
            -114.00
            174.52
            1986.00
            Goods once sold will not be taken back.
        """.trimIndent()

        val result = ReceiptParser.parse(ocr)
        assertEquals(1986.00, result.totalAmount!!, 0.001)
        assertEquals("INR", result.currency)
    }

    @Test
    fun `handles sleek bill 2-inch format`() {
        val sleekBillOcr = """
            Bil No: IN-15
            1
            Unpaid
            SN
            3
            5
            Nirmal Vijay, Panchshil Square, Tapovan Road, Camp, Amravati,
            Item
            Orange Powder
            Walnuts 5% Tax
            Item
            Rose Water
            Coin 3% Tax Item
            Glicerene
            SLEEK BILL
            Item
            PHONE: +911234567890
            GSTIN: 27AAFCV2449G1Z7
            Cheese 12% TaxX
            Subtotal
            TOTAL
            444602, IN
            Qty
            1
            1
            1
            1
            1
            1
            Price
            400.00
            100.00
            100.00
            150.00
            50.00
            100.00
            Date: 23 -Jan -2025
            IGST at 0%
            IGST at 3%
            IGST at 5%
            Thank You
            IGST at 12%
            Amt
            448.00
            105.00
            103.00
            150.00
            50.00
            112.00
            ¿900.00
            0.00
            3.00
            5.00
            60.00
            968.00
        """.trimIndent()

        val result = ReceiptParser.parse(sleekBillOcr)
        assertEquals(968.00, result.totalAmount!!, 0.001)
    }
}
