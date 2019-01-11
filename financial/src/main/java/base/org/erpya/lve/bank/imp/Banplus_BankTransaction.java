/**************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                               *
 * This program is free software; you can redistribute it and/or modify it    		  *
 * under the terms version 2 or later of the GNU General Public License as published  *
 * by the Free Software Foundation. This program is distributed in the hope           *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied         *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                   *
 * See the GNU General Public License for more details.                               *
 * You should have received a copy of the GNU General Public License along            *
 * with this program; if not, printLine to the Free Software Foundation, Inc.,        *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                             *
 * For the text or an alternative of this public license, you may reach us            *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved.  *
 * Contributor: Yamel Senih ysenih@erpya.com                                          *
 * See: www.erpya.com                                                                 *
 *************************************************************************************/
package org.erpya.lve.bank.imp;

import java.math.BigDecimal;
import java.sql.Timestamp;
import org.compiere.util.Util;
import org.spin.util.impexp.BankTransactionAbstract;

/**
 * Custom format for Venezuela Bank Transaction
 * It is a specific solution for a bank
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 * <li> FR [ 1701 ] Add support to MT940 format
 * @see https://github.com/adempiere/adempiere/issues/1701
 */
public class Banplus_BankTransaction extends BankTransactionAbstract {
	/**	Ignore it line because is a first line as head */
	public static final String HEAD_REFERENCE_FIRST_LINE_FLAG = "\"Fecha\"	\"Referencia\"	\"Descripción\"	\"Concepto\"	\"Débito\"	\"Crédito\"	\"Saldo\"";
	/**	Ignore it line because is a begin balance */
	public static final String HEAD_BEGIN_BALANCE_FLAG = "		\"Saldo Inicial\"";
	/**	Value Date [dddMMyyyy]	*/
	public static final String LINE_TRANSACTION_Date = "TrxDate";
	/**	Transaction type Transaction type (description)	*/ 
	public static final String LINE_TRANSACTION_Type = "Type";
	/**	Memo of transaction	*/
	public static final String LINE_TRANSACTION_Memo = "Memo";
	/**	Concept of transaction	*/
	public static final String LINE_TRANSACTION_Concept = "Concept";
	/**	Sequence number [35x] Sequential number of transaction on account	*/
	public static final String LINE_TRANSACTION_ReferenceNo = "ReferenceNo";
	/**	Amount	*/
	public static final String LINE_TRANSACTION_Amount = "Amount";
	/**	Start Column Index	*/
	private static final char START_CHAR_VALUE = '\t';
	/**	Debt Constant	*/
	public static final String DEBT = "DR";
	/**	Credit Constant	*/
	public static final String CREDIT = "CR";
	/**	Is a transaction	*/
	private boolean isTransaction = false;
	
	/**
	 * Parse Line
	 * @param line
	 */
	public void parseLine(String line) throws Exception {
		if(Util.isEmpty(line)) {
			return;
		}
		if(line.contains(HEAD_REFERENCE_FIRST_LINE_FLAG)
				|| line.contains(HEAD_BEGIN_BALANCE_FLAG)) {
			isTransaction = false;
			return;
		}
		//	Validate
		line = processValue(line);
		if(Util.isEmpty(line)) {
			return;
		}
		//	Replace bad characters
		line = line.replaceAll("\"", "");
		//	Set Transaction Date
		addValue(LINE_TRANSACTION_Date, getDate("dd/MM/yyyy", subString(line, 0, 10)));
		//	Set Reference
		int startIndex = 0;
		int endIndex = 0;
		int initPosition = 1;
		String value = null;
		startIndex = line.indexOf(START_CHAR_VALUE) + initPosition;
		endIndex = line.substring(startIndex).indexOf(START_CHAR_VALUE) + startIndex + initPosition;
		value = subString(line, startIndex, endIndex);
		if(!Util.isEmpty(value)) {
			addValue(LINE_TRANSACTION_ReferenceNo, value.replaceAll("	", ""));
		}
		//	
		line = line.substring(endIndex);
		startIndex = 0;
		endIndex = line.indexOf(START_CHAR_VALUE) + initPosition;
		//	Set Memo
		value = subString(line, startIndex, endIndex);
		if(!Util.isEmpty(value)) {
			addValue(LINE_TRANSACTION_Memo, value.replaceAll("	", ""));
		}
		//	Set Concept
		line = line.substring(endIndex);
		startIndex = 0;
		endIndex = line.indexOf(START_CHAR_VALUE) + initPosition;
		//	Set Concept
		value = subString(line, startIndex, endIndex);
		if(!Util.isEmpty(value)) {
			addValue(LINE_TRANSACTION_Concept, value.replaceAll("	", ""));
		}
		//	
		line = line.substring(endIndex);
		startIndex = 0;
		endIndex = line.indexOf(START_CHAR_VALUE) + initPosition;
		//	Set Debt
		BigDecimal debit = getNumber('.', "##################", subString(line, startIndex, endIndex));
		//	
		line = line.substring(endIndex);
		startIndex = 0;
		endIndex = line.indexOf(START_CHAR_VALUE) + initPosition;
		//	Set Credit
		BigDecimal credit = getNumber('.', "##################", subString(line, startIndex, endIndex));
		//	Add to index (ignore balance)
		if(debit != null
				&& debit.doubleValue() != 0) {
			addValue(LINE_TRANSACTION_Amount, debit.negate());
			addValue(LINE_TRANSACTION_Type, DEBT);
		} else if(credit != null
				&& credit.doubleValue() != 0) {
			addValue(LINE_TRANSACTION_Amount, credit);
			addValue(LINE_TRANSACTION_Type, CREDIT);
		}
		//	fine
		isTransaction = true;
	}
	
	/**
	 * Get Bank Transaction Date
	 * @return
	 */
	public Timestamp getTrxDate() {
		return getDate(LINE_TRANSACTION_Date);
	}
	
	/**
	 * Get Amount of transaction
	 * @return
	 */
	public BigDecimal getAmount() {
		return getNumber(LINE_TRANSACTION_Amount);
	}

	/**
	 * Get Payee Account
	 * @return
	 */
	public String getPayeeAccountNo() {
		return null;
	}
	
	/**
	 * Get Memo of Transaction
	 * @return
	 */
	public String getMemo() {
		return getString(LINE_TRANSACTION_Memo);
	}
	
	/**
	 * Get Category
	 * @return
	 */
	public String getTrxType() {
		return getString(LINE_TRANSACTION_Type);
	}
	
	/**
	 * Get Check Numbers
	 * @return
	 */
	public String getCheckNo() {
		return getString(LINE_TRANSACTION_ReferenceNo);
	}
	
	/**
	 * Process or change value for import
	 * you can implement it method for replace special characters
	 * @param value
	 * @return
	 */
	protected String processValue(String value) {
		return value;
	}

	@Override
	public boolean isEndTransactionLine(String line) {
		return true;
	}
	
	@Override
	public boolean isCompleteData() {
		return isTransaction;
	}

	@Override
	public String getCurrency() {
		return null;
	}

	@Override
	public Timestamp getValueDate() {
		return getDate(LINE_TRANSACTION_Date);
	}

	@Override
	public Timestamp getStatementDate() {
		return getDate(LINE_TRANSACTION_Date);
	}

	@Override
	public String getReferenceNo() {
		return getString(LINE_TRANSACTION_ReferenceNo);
	}

	@Override
	public String getTrxCode() {
		return null;
	}
	
	@Override
	public String getPayeeName() {
		return null;
	}

	@Override
	public String getPayeeDescription() {
		return getString(LINE_TRANSACTION_Concept);
	}
}