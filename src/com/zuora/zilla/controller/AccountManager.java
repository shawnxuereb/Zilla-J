package com.zuora.zilla.controller;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

import com.zuora.api.*;
import com.zuora.api.object.*;
import com.zuora.zilla.controller.*;
import com.zuora.zilla.model.*;
import com.zuora.zilla.util.*;

/**
 * The AccountManager class manages Account information for the logged in user.
 * 
 * All account summaries are based on the user that has been authetnicated and
 * whose Email address has been stored in the Session Header. To retrieve any of
 * these summaries, the user must:
 * 
 * 	1) Populate the Session['uEmail'] with their contact email
 * 	2) Call the Get Account Detail Method
 * 
 * @author Mickael Pham <mickael.pham@zuora.com>
 * 
 */
public class AccountManager {

	/** The Zuora API instance used to handle soap calls. */
	private ZApi zapi;
	
	public AccountManager() throws Exception {
		// get the stub and the helper
		try {
			zapi = new ZApi();
		} catch (Exception e) {
			throw new Exception("Invalid Login");
		}
	}
	
	public SummaryAccount getCompleteDetail(String accountName) {
		// Create the object and get the basic information
		SummaryAccount accountSummary = new SummaryAccount();
		
		Account acc = null;
		QueryResult qresAcc = null;
		try {
			qresAcc = zapi.zQuery("SELECT Id,Name,Balance,LastInvoiceDate,DefaultPaymentMethodId FROM Account WHERE Name='" + accountName + "'");
			if(qresAcc.getSize() == 0){
				accountSummary.setSuccess(false);
				accountSummary.setError("USER_DOESNT_EXIST");
				return accountSummary;
			}
			acc = (Account) qresAcc.getRecords()[0];
		} catch (Exception e) {
			e.printStackTrace();
		}

		
		//Get Account Information

		accountSummary.setName(acc.getName());
		accountSummary.setBalance(acc.getBalance());
		accountSummary.setLastInvoiceDate(acc.getLastInvoiceDate());
		String defaultPmId = acc.getDefaultPaymentMethodId();
		
		QueryResult paymentResult = null;
		try {
			paymentResult = zapi.zQuery("SELECT Amount,EffectiveDate,CreatedDate FROM Payment WHERE AccountId='" + acc.getId() + "'");
		} catch (Exception e) {
			e.printStackTrace();
		}
			
		if(paymentResult.getSize()==0){
			accountSummary.setLastPaymentDate(null);
			accountSummary.setLastInvoiceDate(null);
		} else {
			//Sort payments by date
			ArrayList<Payment> listPayments = new ArrayList<Payment>(Arrays.asList((Payment[]) paymentResult.getRecords()));
			Collections.sort(listPayments, new CmpPayments());
			Payment lastPayment = (Payment) listPayments.toArray()[0];
			accountSummary.setLastPaymentDate(lastPayment.getEffectiveDate());
			accountSummary.setLastPaymentAmount(lastPayment.getAmount());
		}

		//Get Contact with this email
		SummaryContact contactSummary = new SummaryContact();
		QueryResult qresCons = null;
		try {
			qresCons = zapi.zQuery("SELECT FirstName,LastName,Address1,Address2,City,State,PostalCode,Country FROM Contact WHERE AccountId='"+ acc.getId() +"'");
		} catch (Exception e) {
			e.printStackTrace();
		}
		Contact cont = null;
		if(qresCons.getSize()==0){
			accountSummary.setSuccess(false);
			accountSummary.setError("CONTACT_DOESNT_EXIST");
			return accountSummary;			
		} else {
			cont = (Contact) qresCons.getRecords()[0];
		}

		contactSummary.setFirstName(cont.getFirstName());
		contactSummary.setLastName(cont.getLastName());
		contactSummary.setCountry(cont.getCountry());
		contactSummary.setState(cont.getState());
		contactSummary.setAddress1(cont.getAddress1());
		contactSummary.setAddress2(cont.getAddress2());
		contactSummary.setCity(cont.getCity());
		contactSummary.setPostalCode(cont.getPostalCode());

		contactSummary.setSuccess(true);

		accountSummary.setContactSummary(contactSummary);

		
		//Get PaymentMethods with this Account Id
		
		
		
		// Get payment methods with this account id
		ArrayList<PaymentDetail> paymentSummaries = new ArrayList<PaymentDetail>();
		
		QueryResult qresPms = null;
		try {
			qresPms = zapi.zQuery("SELECT Id,CreditCardHolderName,CreditCardMaskNumber,"
					+ "CreditCardExpirationYear,CreditCardExpirationMonth,CreditCardType "
					+ "from PaymentMethod WHERE AccountId='" + acc.getId() + "'");
		} catch (Exception e) {
			e.printStackTrace();
		}

		for (ZObject z : qresPms.getRecords()) {
			PaymentMethod pm = (PaymentMethod) z;
			PaymentDetail d = new PaymentDetail();
			d.setId(pm.getId());
			d.setCardHolderName(pm.getCreditCardHolderName());
			d.setMaskedNumber(pm.getCreditCardMaskNumber());
			d.setExpirationYear(pm.getCreditCardExpirationYear());
			d.setExpirationMonth(pm.getCreditCardExpirationMonth());
			d.setCardType(pm.getCreditCardType());
			d.setDefaultCard((pm.getId().equals(defaultPmId)));
			paymentSummaries.add(d);
		}
		accountSummary.setPaymentMethodSummaries(paymentSummaries);
		
		
		accountSummary.setSuccess(true);
		return accountSummary;

	}
	
	/**
	 * Get payment information from the account associated with this email address
	 * @param email User's work email
	 * @return Payment methods list
	 */
	public List<PaymentDetail> getPaymentMethodSummary(String email) {
		// Step #1: retrieve the account ID associated with this email
		Query accountQuery = new Query();
		accountQuery.setQueryString("SELECT AccountId FROM Contact WHERE WorkEmail='" + email + "'");
		String accountId = null;
		try {
			QueryResponse accountResponse = stub.query(accountQuery, this.header);
			Contact c = (Contact) accountResponse.getResult().getRecords()[0];
			accountId = c.getAccountId().getID();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Account does not exist!
		if (accountId == null) {
			return null;
		}
		
		// Get default payment method id for this account
		String defaultPaymentMethodId = null;
		Query defaultPmQuery = new Query();
		defaultPmQuery.setQueryString("SELECT DefaultPaymentMethodId FROM Account WHERE Id='" + accountId + "'");
		try {
			QueryResponse resp = stub.query(defaultPmQuery, this.header);
			Account acc = (Account) resp.getResult().getRecords()[0];
			System.out.println(accountId);
			defaultPaymentMethodId = acc.getDefaultPaymentMethodId().getID();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Payment method doesn't exist
		if (defaultPaymentMethodId == null) {
			return null;
		}
		
		// Prepare the list of payment detail
		List<PaymentDetail> details = new ArrayList<PaymentDetail>();
		
		// Get payment methods with this account id
		Query paymentsQuery = new Query();
		paymentsQuery.setQueryString("SELECT Id,CreditCardHolderName,CreditCardMaskNumber,"
					+ "CreditCardExpirationYear,CreditCardExpirationMonth,CreditCardType "
					+ "from PaymentMethod WHERE AccountId='" + accountId + "'");
		try {
			QueryResponse resp = stub.query(paymentsQuery, this.header);
			for (ZObject z : resp.getResult().getRecords()) {
				PaymentMethod pm = (PaymentMethod) z;
				PaymentDetail d = new PaymentDetail();
				d.setId(pm.getId().getID());
				d.setCardHolderName(pm.getCreditCardHolderName());
				d.setMaskedNumber(pm.getCreditCardMaskNumber());
				d.setExpirationYear(pm.getCreditCardExpirationYear());
				d.setExpirationMonth(pm.getCreditCardExpirationMonth());
				d.setCardType(pm.getCreditCardType());
				d.setDefaultCard((pm.getId().getID().equals(defaultPaymentMethodId)));
				details.add(d);
			}
 		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return details;
	}
	
	/**
	 * Update the given user's information
	 * @return the user ID if successful
	 */
	public String updateContact(String accountName, SummaryContact updatedContact) {
		
		// Get Account ID with this name
		String accountId = null;
		Query accountQuery = new Query();
		accountQuery.setQueryString("SELECT Id FROM Account WHERE Name='" + accountName + "'");
		
		try {
			QueryResult result = this.stub.query(accountQuery, header).getResult();
			Account acc = (Account) result.getRecords()[0];
			accountId = acc.getId().getID();
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		// Get the contact ID associated with this account
		ID contactId = null;
		Query contactQuery = new Query();
		contactQuery.setQueryString("SELECT Id FROM Contact WHERE AccountId='" + accountId + "'");
		
		try {
			QueryResult result = this.stub.query(contactQuery, header).getResult();
			Contact contact = (Contact) result.getRecords()[0];
			contactId = contact.getId();
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		// Create a contact record with this ID and all parameters that were passed in
		Contact updated = new Contact();
		updated.setId(contactId);
		updated.setFirstName(updatedContact.getFirstName());
		updated.setLastName(updatedContact.getLastName());
		updated.setCountry(updatedContact.getCountry());
		updated.setAddress1(updatedContact.getAddress1());
		updated.setAddress2(updatedContact.getAddress2());
		updated.setPostalCode(updatedContact.getPostalCode());
		updated.setCity(updatedContact.getCity());
		updated.setState(updatedContact.getState());
		
		String returnedId = null;
		
		try {
			returnedId = this.helper.update(updated).getID();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return returnedId;
	}
	
	/**
	 * Comparator to sort payments by effective date.
	 */
	private class CmpPayments implements Comparator<Payment> {

		@Override
		public int compare(Payment a, Payment b) {
			return a.getCreatedDate().compareTo(b.getCreatedDate());
		}
		
	}

	public boolean checkEmailAvailability(String targetEmail) {
		// Prepare the zQuery
		Query query = new Query();
		query.setQueryString("SELECT Id FROM Contact WHERE WorkEmail = '"
				+ targetEmail + "'");
		try {
			QueryResponse resp = stub.query(query, this.header);
			if (resp != null) {
				if (resp.getResult().getSize() != 0) {
					return false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return true;
	}

}
