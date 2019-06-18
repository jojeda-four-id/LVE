/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, C.A.                      *
 * Contributor(s): Yamel Senih ysenih@erpya.com                                      *
 * This program is free software: you can redistribute it and/or modify              *
 * it under the terms of the GNU General Public License as published by              *
 * the Free Software Foundation, either version 3 of the License, or                 *
 * (at your option) any later version.                                               *
 * This program is distributed in the hope that it will be useful,                   *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                    *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                     *
 * GNU General Public License for more details.                                      *
 * You should have received a copy of the GNU General Public License                 *
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.            *
 ************************************************************************************/
package org.erpya.lve.util;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.compiere.model.I_C_Invoice;
import org.compiere.model.MBPartner;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceTax;
import org.compiere.model.MTax;
import org.compiere.util.Env;
import org.erpya.lve.model.MLVEList;
import org.erpya.lve.model.MLVEWithholdingTax;
import org.spin.model.I_WH_Withholding;
import org.spin.model.MWHSetting;
import org.spin.util.AbstractWithholdingSetting;

/**
 * 	Implementación de retención de I.V.A para la localización de Venezuela
 * 	Esto puede aplicar para Documentos por Pagar y Notas de Crédito de Documentos por Pagar
 * 	Note que la validación de las 20 UT solo aplica para documentos por pagar
 * 	@author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 */
public class APInvoiceIVA extends AbstractWithholdingSetting {

	public APInvoiceIVA(MWHSetting setting) {
		super(setting);
	}
	/**	Current Invoice	*/
	private MInvoice invoice;
	/**	Current Business Partner	*/
	private MBPartner businessPartner;
	/**	Withholding tax rate	*/
	private BigDecimal withholdingRate;
	/**	Taxes	*/
	private List<MInvoiceTax> taxes;
	/**	Withholding Tax Applied for Tax	*/
	private final String WITHHOLDING_APPLIED = "IsWithholdingTaxApplied";
	/**	Custom Tax Rate for Business Partner	*/
	private final String WITHHOLDING_TAX_RATE = "WithholdingTaxRate_ID";
	/**	Withholding Tax Exempt for Business Partner	*/
	private final String WITHHOLDING_TAX_EXEMPT = "IsWithholdingTaxExempt";
	/**	Minimum Tribute Unit for apply Withholding Tax	*/
	private final int MINIMUM_TRIBUTE_UNIT = 20;
	
	
	@Override
	public boolean isValid() {
		boolean isValid = true;
		//	Validate Document
		if(getDocument().get_Table_ID() != I_C_Invoice.Table_ID) {
			addLog("@C_Invoice_ID@ @NotFound@");
			isValid = false;
		}
		invoice = (MInvoice) getDocument();
		//	Add reference
		setReturnValue(I_WH_Withholding.COLUMNNAME_SourceInvoice_ID, invoice.getC_Invoice_ID());
		//	Validate if exists Withholding Tax Definition for client
		if(MLVEWithholdingTax.getFromClient(getCtx(), getDocument().getAD_Org_ID()) == null) {
			addLog("@LVE_WithholdingTax_ID@ @NotFound@");
			isValid = false;
		}
		//	Validate Reversal
		if(invoice.isReversal()) {
			addLog("@C_Invoice_ID@ @Voided@");
			isValid = false;
		}
		MDocType documentType = MDocType.get(getCtx(), invoice.getC_DocTypeTarget_ID());
		if(documentType == null) {
			addLog("@C_DocType_ID@ @NotFound@");
			isValid = false;
		}
		//	Validate AP only
		if(!documentType.getDocBaseType().equals(MDocType.DOCBASETYPE_APInvoice)
				&& !documentType.getDocBaseType().equals(MDocType.DOCBASETYPE_APCreditMemo)) {
			addLog("@APDocumentRequired@");
			isValid = false;
		}
		//	Validate Exempt Document
		if(invoice.get_ValueAsBoolean(WITHHOLDING_TAX_EXEMPT)) {
			isValid = false;
			addLog("@DocumentWithholdingTaxExempt@");
		}
		//	Validate Exempt Business Partner
		if(businessPartner.get_ValueAsBoolean(WITHHOLDING_TAX_EXEMPT)) {
			isValid = false;
			addLog("@BPartnerWithholdingTaxExempt@");
		}
		//	Validate Withholding Definition
		MLVEWithholdingTax withholdingTaxDefinition = MLVEWithholdingTax.getFromClient(getCtx(), invoice.getAD_Org_ID());
		int withholdingRateId = businessPartner.get_ValueAsInt(WITHHOLDING_TAX_RATE);
		if(withholdingRateId == 0) {
			withholdingRateId = withholdingTaxDefinition.getDefaultWithholdingRate_ID();
		}
		//	Validate Definition
		if(withholdingRateId == 0) {
			addLog("@" + WITHHOLDING_TAX_RATE + "@ @NotFound@");
			isValid = false;
		} else {
			withholdingRate = MLVEList.get(getCtx(), withholdingRateId).getListVersionAmount(invoice.getDateInvoiced());
		}
		//	Validate Tax
		if(withholdingRate.equals(Env.ZERO)) {
			addLog("@LVE_WithholdingTax_ID@ (@Rate@ @NotFound@)");
			isValid = false;
		}
		//	Validate Tribute Unit
		BigDecimal tributeUnitAmount = withholdingTaxDefinition.getValidTributeUnitAmount(invoice.getDateInvoiced());
		if(tributeUnitAmount.equals(Env.ZERO)) {
			addLog("@TributeUnit@ (@Rate@ @NotFound@)");
			isValid = false;
		}
		//	Validate Minimum Tribute Unit (Only for AP Invoice)
		if(documentType.getDocBaseType().equals(MDocType.DOCBASETYPE_APInvoice)) {
			BigDecimal minTributeUnitAmount = tributeUnitAmount.multiply(new BigDecimal(MINIMUM_TRIBUTE_UNIT));
			if(minTributeUnitAmount.compareTo(invoice.getGrandTotal()) > 0) {
				addLog("@MinimumTributeUnitRequired@ " + MINIMUM_TRIBUTE_UNIT);
				isValid = false;
			}
		}
		//	Validate if it have taxes
		taxes = Arrays.asList(invoice.getTaxes(false))
			.stream()
			.filter(invoiceTax -> MTax.get(getCtx(), invoiceTax.getC_Tax_ID()).get_ValueAsBoolean(WITHHOLDING_APPLIED) 
					&& invoiceTax.getTaxAmt() != null 
					&& invoiceTax.getTaxAmt().compareTo(Env.ZERO) > 0)
			.collect(Collectors.toList());
		if(taxes.size() == 0) {
			addLog("@NoTaxesForWithholding@");
			isValid = false;
		}
		//	
		return isValid;
	}

	@Override
	public String run() {
		setWithholdingRate(withholdingRate);
		withholdingRate = withholdingRate.divide(Env.ONEHUNDRED);
		//	Iterate
		taxes.forEach(invoiceTax -> {
			addBaseAmount(invoiceTax.getTaxAmt());
			addWithholdingAmount(invoiceTax.getTaxAmt().multiply(withholdingRate));
			MTax tax = MTax.get(getCtx(), invoiceTax.getC_Tax_ID());
			addDescription(tax.getName() + " @Processed@");
		});
		return null;
	}
}