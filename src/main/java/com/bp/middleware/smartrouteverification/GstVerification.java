package com.bp.middleware.smartrouteverification;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.bp.middleware.customexception.InvalidApiKeyOrApplicationIdException;
import com.bp.middleware.duplicateverificationresponse.GstReplica;
import com.bp.middleware.duplicateverificationresponse.PanReplica;
import com.bp.middleware.erroridentifier.ErrorIdentifierRepository;
import com.bp.middleware.erroridentifier.ErrorIdentifierService;
import com.bp.middleware.merchantapipricesetup.MerchantPriceModel;
import com.bp.middleware.merchantapipricesetup.MerchantPriceRepository;
import com.bp.middleware.requestandresponse.Request;
import com.bp.middleware.requestandresponse.RequestRepository;
import com.bp.middleware.requestandresponse.Response;
import com.bp.middleware.requestandresponse.ResponseRepository;
import com.bp.middleware.responsestructure.ResponseStructure;
import com.bp.middleware.user.EntityModel;
import com.bp.middleware.user.RequestModel;
import com.bp.middleware.user.UserRepository;
import com.bp.middleware.util.AppConstants;
import com.bp.middleware.util.DateUtil;
import com.bp.middleware.util.FileUtils;
import com.bp.middleware.util.GetPublicIpAndLocation;
import com.bp.middleware.util.PasswordUtils;
import com.bp.middleware.vendorapipricesetup.VendorPriceModel;
import com.bp.middleware.vendorapipricesetup.VendorPriceRepository;
import com.bp.middleware.vendors.VendorModel;
import com.bp.middleware.vendors.VendorRepository;
import com.bp.middleware.vendors.VendorVerificationModel;
import com.bp.middleware.vendors.VendorVerificationRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class GstVerification {

	
	private final String VERIFICATION_TYPE = AppConstants.GST_VERIFY;
	private EntityModel ENTITY = null;
	
	@Autowired
	private ResponseRepository respRepository;
	@Autowired
	private RequestRepository reqRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private MerchantPriceRepository merchantPriceRepository;
	@Autowired
	private VendorRepository vendorRepository;
	@Autowired
	private VendorVerificationRepository vendorVerificationRepository;
	@Autowired
	private VendorPriceRepository vendorPriceRepository;
	@Autowired
	private SmartRouteUtils smartRouteUtils;
	@Autowired
	private ErrorIdentifierService errorIdentifierService;
	@Autowired
	private  CommonResponseStructure CommonResponseStructure;
	@Autowired
	private GstReplica gstReplica;
	@Autowired
	private GetPublicIpAndLocation ipAndLocation;

	public ResponseStructure gstVerification(RequestModel model, HttpServletRequest servletRequest) {
		ResponseStructure structure = new ResponseStructure();
		try {

			String apiKey = servletRequest.getHeader("x-parse-rest-api-key");
			String applicationId = servletRequest.getHeader("x-parse-application-id");

			EntityModel userModel = userRepository.findByApiKeyAndApplicationId(apiKey, applicationId);
			
			if (userModel == null) {
				userModel = userRepository.findByApiSandboxKeyAndApplicationId(apiKey, applicationId);
			}

			if (userModel != null && userModel.isAccountStatus()) {

				ENTITY=userModel;
				
				VendorVerificationModel vendorVerifyModel = vendorVerificationRepository
						.findByVerificationDocument(AppConstants.GST_VERIFY);

				if(!vendorVerifyModel.isStatus()) {
					
					return smartRouteUtils.verificationCurrentlyNotAvailable(userModel, vendorVerifyModel,model);
				}
				
				List<MerchantPriceModel> merchantPriceList = merchantPriceRepository
						.findByEntityModelAndVendorVerificationModelAndStatus(userModel, vendorVerifyModel, true);
				
                boolean accepted = true;
				
				for (MerchantPriceModel merchantPriceModel : merchantPriceList) {
					
					if(accepted) {
						
						accepted = merchantPriceModel.isAccepted();
					}else {
						 break ;
					}
				}


				List<VendorPriceModel> vendorPriceList = vendorPriceRepository
						.findByVendorVerificationModelAndStatus(vendorVerifyModel, true);

				if (!merchantPriceList.isEmpty() && !vendorPriceList.isEmpty() &&  accepted) {
					
					if(userModel.getApiSandboxKey().equals(apiKey) && userModel.getNoRestriction()==0) {
						
						return gstReplica.gstDuplicateResponse(model,userModel,vendorVerifyModel);
						
					}else if(userModel.getNoRestriction()>0) {
						
						userModel.setNoRestriction(userModel.getNoRestriction()-1);
						
						model.setFreeHit(true);
					}

					MerchantPriceModel merchantPriceModel = merchantPriceList.get(0);

					ResponseStructure balanceCheck = smartRouteUtils.balanceCheck(userModel, merchantPriceModel,vendorVerifyModel);

					if (balanceCheck.getFlag() == 1) {

						String userDecryption = PasswordUtils.demoDecrypt(model.getEncrypted_data(),
								userModel.getSecretKey());

						JSONObject userJson = new JSONObject(userDecryption);

						String referenceNumber = FileUtils.getRandomOTPnumber(10);
						userJson.put("reference_id", referenceNumber);
						userJson.put("source_type", "id");

						String source = userJson.getString("source");

						System.err.println("SOURCE : " + source);

						Response sourceResponse = smartRouteUtils.sourceCheck(source, userModel, merchantPriceModel);
						System.err.println("Source Response : " + sourceResponse);

						if (sourceResponse.getResponseId() > 0) {
							System.err.println("Source IF");

							Response response = smartRouteUtils.setRequest(sourceResponse, model, merchantPriceModel,
									userModel, vendorVerifyModel, userJson);

							JSONObject jsonSource = new JSONObject(response.getCommonResponse());

							LocalDateTime dateTime = LocalDateTime.now();
							DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
							String responseTime = dateTime.format(format);

							jsonSource.put("response_time", responseTime);
							jsonSource.put("reference_id", referenceNumber);

							String commonResponse = PasswordUtils.demoEncryption(jsonSource, userModel.getSecretKey());

							Map<String, Object> mapNew = new HashMap<>();
							mapNew.put("return_response", commonResponse);

							structure.setData(mapNew);
							structure.setStatusCode(HttpStatus.OK.value());
							structure.setFlag(1);
							structure.setMessage(AppConstants.SUCCESS);

							return structure;

						} else {
							System.err.println("Source ELSE");
							return gstVerificationSmartRoute(userJson, model, userModel, vendorVerifyModel);
						}
					}

					return balanceCheck;

				} else {

					if(vendorPriceList.isEmpty()) {
						return smartRouteUtils.verificationCurrentlyNotAvailable(userModel, vendorVerifyModel,model);
					}else if(merchantPriceList.isEmpty()){
						return smartRouteUtils.noAccessForThisVerification(userModel, vendorVerifyModel,model);
					}else {
						return smartRouteUtils.notAccepted(userModel, vendorVerifyModel,model);
					}
				}
			} else {

				if(userModel == null) {
					return smartRouteUtils.commonErrorResponse();
				}else {
					return smartRouteUtils.accountInactive(userModel);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			
			structure.setErrorReferenceId(errorIdentifierService.errorSaver(e, VERIFICATION_TYPE,ENTITY));
			structure.setFileName(VERIFICATION_TYPE);
			structure.setData(null);
			structure.setMessage(AppConstants.ERROR_MESSAGE_RESPONSE);
			structure.setErrorDiscription(AppConstants.ERROR_DESCRIPTION_RESPONSE);
			structure.setFlag(7);
			structure.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
		}
		return structure;
	}

	private ResponseStructure gstVerificationSmartRoute(JSONObject userJson, RequestModel model, EntityModel userModel,
			VendorVerificationModel vendorVerifyModel) throws Exception {

		List<Request> reqList = reqRepository.findByUserAndVerificationModel(userModel, vendorVerifyModel);

		Request lastRequest = new Request();

		InetAddress ipAddressLocalHost = InetAddress.getLocalHost();
		String ipAddress = ipAddressLocalHost.getHostAddress();

		long timeDifference = 0;
		if (!reqList.isEmpty()) {
			lastRequest = reqList.get(reqList.size() - 1);

			Date currentDatetime = new Date();
			Date requestDatetime = lastRequest.getRequestDateAndTime();

			timeDifference = DateUtil.secondsDifferenceCalculator(requestDatetime, currentDatetime);
		}

		int attempt = lastRequest.getAttempt();
//		int priority = attempt + 1;

		model.setAttempt(attempt);

		if (reqList.isEmpty() || attempt == 0 || timeDifference > 60) // first Priority -
		{
			System.err.println("ATTEMPT 0 & TimeDifference : " + timeDifference);
			MerchantPriceModel merchantPriority = merchantPriceRepository
					.findByEntityModelAndVendorVerificationModelAndPriority(userModel, vendorVerifyModel, 1);
			return requestVendorRouting(userJson, model, merchantPriority, userModel, vendorVerifyModel);

		} else if (attempt == 1 && timeDifference < 120) // Second Priority -
		{
			System.err.println("ATTEMPT 1 & TimeDifference : " + timeDifference);
			MerchantPriceModel merchantPriority = merchantPriceRepository
					.findByEntityModelAndVendorVerificationModelAndPriority(userModel, vendorVerifyModel, 2);
			return requestVendorRouting(userJson, model, merchantPriority, userModel, vendorVerifyModel);

		} else if (attempt == 2 && timeDifference < 120) // Second Priority -
		{
			System.err.println("ATTEMPT 2 & TimeDifference : " + timeDifference);
			MerchantPriceModel merchantPriority = merchantPriceRepository
					.findByEntityModelAndVendorVerificationModelAndPriority(userModel, vendorVerifyModel, 3);
			return requestVendorRouting(userJson, model, merchantPriority, userModel, vendorVerifyModel);

		} else // Common Priority
		{
			System.err.println("ATTEMPT 4 COMMON");

			VendorModel highSuccessVendor = smartRouteUtils.vendorSuccessRate(vendorVerifyModel);

			MerchantPriceModel merchantPriority = merchantPriceRepository
					.findByEntityModelAndVendorModelAndVendorVerificationModel(userModel, highSuccessVendor,
							vendorVerifyModel);

			return requestVendorRouting(userJson, model, merchantPriority, userModel, vendorVerifyModel);
		}
	}

	private ResponseStructure requestVendorRouting(JSONObject userJson, RequestModel model,
			MerchantPriceModel merchantPriceModel, EntityModel userModel, VendorVerificationModel vendorVerifyModel)
			throws Exception {

		VendorModel vendorModel = merchantPriceModel.getVendorModel();
		VendorPriceModel vendorPrice = vendorPriceRepository.findByVendorModelAndVendorVerificationModel(vendorModel,
				vendorVerifyModel);

		ResponseStructure balanceCheck = smartRouteUtils.balanceCheck(userModel, merchantPriceModel,vendorVerifyModel);

		if (balanceCheck.getFlag() == 1) {

			if (vendorModel.getVendorName().equalsIgnoreCase(AppConstants.SIGN_DESK_VENDOR)) {

				if(!vendorModel.isStatus()) {
					smartRouteUtils.verificationCurrentlyNotAvailable(userModel, vendorVerifyModel, model);
				}
				
				System.err.println("SIGN DESK");
				return signDeskGstVerification(userJson, model, userModel, vendorVerifyModel, vendorModel,
						merchantPriceModel, vendorPrice);

			} else if (vendorModel.getVendorName().equalsIgnoreCase(AppConstants.SPRINT_VERIFY_VENDOR)) {

				if(!vendorModel.isStatus()) {
					smartRouteUtils.verificationCurrentlyNotAvailable(userModel, vendorVerifyModel, model);
				}
				
				System.err.println("SPRINT V");

				return sprintVerifyGstVerification(userJson, model, userModel, vendorVerifyModel, vendorModel,
						merchantPriceModel, vendorPrice);

			} else if (vendorModel.getVendorName().equalsIgnoreCase(AppConstants.SUREPASS_VENDOR)) {

				if(!vendorModel.isStatus()) {
					smartRouteUtils.verificationCurrentlyNotAvailable(userModel, vendorVerifyModel, model);
				}
				
				System.err.println("SUREPASS");

				return surepassGstVerification(userJson, model, userModel, vendorVerifyModel, vendorModel,
						merchantPriceModel, vendorPrice);
			}
		}

		return balanceCheck;
	}

	private ResponseStructure signDeskGstVerification(JSONObject userJson, RequestModel model, EntityModel userModel,
			VendorVerificationModel vendorVerifyModel, VendorModel vendorModel, MerchantPriceModel merchantPriceModel,
			VendorPriceModel vendorPrice) throws Exception {

		ResponseStructure structure = new ResponseStructure();

		String userReferenceId = userJson.optString("reference_id","");
		String source = userJson.optString("source","");
		String sourceType = userJson.optString("source_type","");
		boolean filingStatus = userJson.getBoolean("filing_status_get");

		Request request = new Request();
		Date reqDate = new Date();

		JSONObject inputParams = new JSONObject();
		JSONObject encryptDatas = new JSONObject();

		inputParams.put("reference_id", userReferenceId);
		inputParams.put("source_type", "id");
		inputParams.put("source", source);
		inputParams.put("filing_status_get", filingStatus);

		System.out.println("Reference id in = " + inputParams.toString());
		String key = AppConstants.ENCRYPTION_KEY;
		// String jsonString = inputParams.toString();
		String encryptedJson = PasswordUtils.demoEncryptionECB(inputParams);
		System.out.println("Encryption Json =" + encryptedJson);

		encryptDatas.put("api_data", encryptedJson);
		encryptDatas.put("enc_mode", "symmetric");
		encryptDatas.put("is_encrypted", true);

		// create an instance of RestTemplate
		RestTemplate restTemplate = new RestTemplate();

		// create headers
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		headers.add("x-parse-rest-api-key", vendorPrice.getApiKey());
		headers.add("x-parse-application-id", vendorPrice.getApplicationId());
		headers.add("Content-Type", AppConstants.CONTENT_TYPE);


		HttpEntity<String> entity = new HttpEntity<>(encryptDatas.toString(), headers);

		ResponseEntity<String> clientResponse = restTemplate.postForEntity(vendorPrice.getApiLink(), entity,
				String.class);
		String data = clientResponse.getBody();

		// VendorReq
		vendorModel.setVendorRequest(vendorModel.getVendorRequest()+1);
		// VendorResponse
		vendorModel.setVendorResponse(vendorModel.getVendorResponse()+1);
		// MonthlyCount
		vendorModel.setMonthlyCount(vendorModel.getMonthlyCount()+1);

		vendorRepository.save(vendorModel);

		// Convert the string to a JSONObject
		JSONObject jsonObject = new JSONObject(data);
		
		String status = jsonObject.optString("status", "");

		if (status.equalsIgnoreCase("failed")) {

			String errorCode = jsonObject.optString("error_code", "");

			if (errorCode.equalsIgnoreCase("vcip_017")) {

				String error = jsonObject.optString("error", "");

				throw new InvalidApiKeyOrApplicationIdException("SIGN DESK : " + error, 522);
			}
		}

		// Extract specific data
		String encryptedResponse = jsonObject.getString("encrypted_response");

		// // Print the extracted data
		System.out.println("encrypted_response: " + encryptedResponse);
		System.out.println("status: " + status);
		//
		String decryptData = PasswordUtils.decryptString(encryptedResponse, key);
		// String decryptData=PasswordUtils.jsonDecryption(encryptedResponse);

		Response response = new Response();

		String ipAddress = ipAndLocation.publicIpAddress();
		request.setIpAddress(ipAddress);
		response.setIpAddress(ipAddress);

		JSONObject decryptJson = new JSONObject(decryptData);

		String signDeskStatus = decryptJson.optString("status","");
		String responseTimeStamp = decryptJson.optString("response_time_stamp","");
		String referenceId = decryptJson.optString("reference_id","");

		System.out.println("Decrypted Response = " + decryptData);

		request.setReferenceId(referenceId);
		request.setSource(source);
		request.setSourceType(sourceType);
		request.setRequestBy(model.getRequestBy());
		request.setRequestDateAndTime(reqDate);
		request.setPrice(merchantPriceModel.getIdPrice());
		request.setStatus(status);
		request.setResponseDateAndTime(responseTimeStamp);
		request.setUser(userModel);
		request.setVerificationModel(vendorVerifyModel);
		request.setFreeHit(model.isFreeHit());
		request.setConsider(true);

		response.setResponse(decryptData);
		response.setReferenceId(referenceId);
		response.setStatus(signDeskStatus);
		response.setSourceType(sourceType);
		response.setSource(source);
		response.setRequestDateAndTime(reqDate);
		response.setRequestBy(model.getRequestBy());
		response.setUser(userModel);
		response.setVendorModel(vendorModel);
		response.setResponseDateAndTime(responseTimeStamp);
		response.setRequest(request);
		response.setVerificationModel(vendorVerifyModel);

		JSONObject object = new JSONObject();
		object.put("status", signDeskStatus);
		object.put("encrypted_response", decryptData);

		RequestModel temporary = new RequestModel();

		temporary.setStatus(signDeskStatus);
		temporary.setReferenceId(referenceId);
		temporary.setResponseDateAndTime(responseTimeStamp);
		temporary.setVendorModel(vendorModel);

		String errorCode ="";
		
		if (signDeskStatus.equals("success")) {

			String message = decryptJson.optString("message","");
			JSONObject resultJson = decryptJson.getJSONObject("result");
			JSONObject validatedJson = resultJson.getJSONObject("validated_data");

			String gstIn = validatedJson.optString("gstin","");
			String businessName = validatedJson.optString("business_name","");
			String transactionId = decryptJson.optString("transaction_id","");
			String dateOfReg = validatedJson.optString("date_of_registration","");
			String dateOfCancel = validatedJson.optString("date_of_cancellation","");
			String panNumber = validatedJson.optString("pan_number","");
			String address = validatedJson.optString("address","");

			String legalName = validatedJson.optString("legal_name","");
			String centerJurisdiction = validatedJson.optString("center_jurisdiction","");
			String stateJurisdiction = validatedJson.optString("state_jurisdiction","");
			String constitutionOfBusiness = validatedJson.optString("constitution_of_business","");
			String taxpayerType = validatedJson.optString("taxpayer_type","");
			String gstInStatus = validatedJson.optString("gstin_status","");
			String fieldVisitConducted = validatedJson.optString("field_visit_conducted","");
			String coreBusinessActivityCode = validatedJson.optString("nature_of_core_business_activity_code","");
			String coreBusinessActivityDescription = validatedJson
					.optString("nature_of_core_business_activity_description","");
			String aadharValidation = validatedJson.optString("aadhaar_validation","");
			String aadharValidatedDate = validatedJson.optString("aadhaar_validation_date","");

			if (filingStatus) {

				JSONArray filingStatusJsonList = validatedJson.getJSONArray("filing_status");
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			} else {
				JSONArray filingStatusJsonList = new JSONArray();
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			}

			request.setMessage(message);
			request.setTransactionId(transactionId);
			request.setFilingStatus(filingStatus);
			request.setAttempt(0);

			response.setAddress(address);
			response.setTransactionId(transactionId);
			response.setMessage(message);
			response.setEncryptedJson(encryptedJson);
			response.setGstIn(gstIn);
			response.setPanNumber(panNumber);
			response.setBusinessName(businessName);
			response.setFilingStatus(filingStatus);
			response.setDateOfRegistration(dateOfReg);
			response.setDateOfCancellation(dateOfCancel);

			temporary.setMessage(message);
			temporary.setSource(gstIn);
			temporary.setPanNumber(panNumber);
			temporary.setBusinessName(businessName);
			temporary.setLegalName(legalName);
			temporary.setCenterJurisdiction(centerJurisdiction);
			temporary.setStateJurisdiction(stateJurisdiction);
			temporary.setDateOfRegistration(dateOfReg);
			temporary.setDateOfCancellation(dateOfCancel);
			temporary.setConstitutionOfBusiness(constitutionOfBusiness);
			temporary.setTaxpayerType(taxpayerType);
			temporary.setGstInStatus(gstInStatus);
			temporary.setFieldVisitConducted(fieldVisitConducted);
			temporary.setCoreBusinessActivityCode(coreBusinessActivityCode);
			temporary.setCoreBusinessActivityDescription(coreBusinessActivityDescription);
			temporary.setAadharValidation(aadharValidation);
			temporary.setAadharValidatedDate(aadharValidatedDate);
			temporary.setAddress(address);

		} else {
			String error = decryptJson.optString("error","");
			errorCode = decryptJson.optString("error_code","");

			request.setError(error);
			request.setErrorCode(errorCode);
			request.setAttempt(model.getAttempt() + 1);

			response.setError(error);
			response.setErrorCode(errorCode);

			temporary.setError(error);
		}
		
		smartRouteUtils.signDeskErrorCodes(errorCode,vendorModel,userModel,vendorVerifyModel);
		
		JSONObject commonResponse = CommonResponseStructure.commonResponseGst(temporary);
		response.setCommonResponse(commonResponse.toString());

		reqRepository.save(request);
		respRepository.save(response);

		// Response Count
		int responseCount = userModel.getResponseCount();
		userModel.setResponseCount(++responseCount);

		
		// Prepaid Amount Reduction
		if (userModel.getPaymentMethod().getPaymentType().equalsIgnoreCase("Prepaid") &&  !model.isFreeHit()  && smartRouteUtils.signDeskError(errorCode)) {

			smartRouteUtils.deductAmountForId(userModel,merchantPriceModel);
			
		}else if(!model.isFreeHit() && smartRouteUtils.signDeskError(errorCode)) {
			
			smartRouteUtils.postpaidConsumedAmount(userModel,merchantPriceModel);
			
		}else {
			
			request.setConsider(false);
			reqRepository.save(request);
		}
		
		String encryptedCommonResponse = PasswordUtils.demoEncryption(commonResponse, userModel.getSecretKey());

		Map<String, Object> mapNew = new HashMap<>();
		mapNew.put("return_response", encryptedCommonResponse);

		structure.setStatusCode(HttpStatus.OK.value());
		structure.setFlag(1);
		structure.setData(mapNew);
		structure.setMessage(AppConstants.SUCCESS);

		userRepository.save(userModel);

		return structure;
	}

	
	
	
	private ResponseStructure sprintVerifyGstVerification(JSONObject userJson, RequestModel model,
			EntityModel userModel, VendorVerificationModel vendorVerifyModel, VendorModel vendorModel,
			MerchantPriceModel merchantPriceModel, VendorPriceModel vendorPrice) throws Exception {

		ResponseStructure structure = new ResponseStructure();

		String source = userJson.getString("source");
		String sourceType = userJson.getString("source_type");
		boolean fileStatus = userJson.getBoolean("filing_status_get");
		String filingStatus = Boolean.toString(fileStatus);

		JSONObject obj = new JSONObject();
		obj.put("id_number", source);
		obj.put("filing_status", filingStatus);

		Request request = new Request();
		Date reqDate = new Date();

		String sprintVerifyResponse = smartRouteUtils.sprintVerifyDocument(obj, merchantPriceModel);

		JSONObject wholeData = new JSONObject(sprintVerifyResponse);
		System.err.println("whole Data " + wholeData);

		// VendorReq
		vendorModel.setVendorRequest(vendorModel.getVendorRequest()+1);
		// VendorResponse
		vendorModel.setVendorResponse(vendorModel.getVendorResponse()+1);
		// MonthlyCount
		vendorModel.setMonthlyCount(vendorModel.getMonthlyCount()+1);

		vendorRepository.save(vendorModel);

		int statusCodeNumber = wholeData.getInt("statuscode");
		String statusCode = Integer.toString(statusCodeNumber);
		boolean status = wholeData.getBoolean("status");
		String message = wholeData.getString("message");

		smartRouteUtils.errorCodes(statusCodeNumber, vendorModel,userModel,vendorVerifyModel);
		
		Response response = new Response();
		LocalDateTime dateTime = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
		String responseTime = dateTime.format(format);

		String ipAddress = ipAndLocation.publicIpAddress();
		request.setIpAddress(ipAddress);
		response.setIpAddress(ipAddress);

		request.setSource(source);
		request.setFilingStatus(fileStatus);
		request.setSourceType(sourceType);
		request.setRequestBy(model.getRequestBy());
		request.setRequestDateAndTime(reqDate);
		request.setPrice(merchantPriceModel.getIdPrice());
		request.setUser(userModel);
		request.setVerificationModel(vendorVerifyModel);
		request.setResponseDateAndTime(responseTime);
		request.setMessage(message);
		request.setFreeHit(model.isFreeHit());
		request.setConsider(true);

		response.setMessage(message);
		response.setSourceType(sourceType);
		response.setSource(source);
		response.setFilingStatus(fileStatus);
		response.setRequestDateAndTime(new Date());
		response.setRequestBy(model.getRequestBy());
		response.setUser(userModel);
		response.setRequest(request);
		response.setVendorModel(vendorModel);
		response.setResponseDateAndTime(responseTime);
		response.setResponse(sprintVerifyResponse);
		response.setVerificationModel(vendorVerifyModel);

		RequestModel temporary = new RequestModel();

		temporary.setResponseDateAndTime(responseTime);
		temporary.setMessage(message);
		temporary.setStatusCodeNumber(statusCodeNumber);
		temporary.setVendorModel(vendorModel);

		if (status) {

			long referenceNumber = wholeData.getLong("reference_id");
			String referenceId = Long.toString(referenceNumber);

			JSONObject internalData = wholeData.getJSONObject("data");

			String clientId = internalData.optString("client_id","");
			String panNumber = internalData.optString("pan_number","");
			String gstIn = internalData.optString("gstin","");
			String businessName = internalData.optString("business_name","");
			String dateOfReg = internalData.optString("date_of_registration","");
			String dateOfCancellation = internalData.optString("date_of_cancellation","");
			String address = internalData.optString("address","");

			String legalName = internalData.optString("legal_name","");
			String centerJurisdiction = internalData.optString("center_jurisdiction","");
			String stateJurisdiction = internalData.optString("state_jurisdiction","");
			String constitutionOfBusiness = internalData.optString("constitution_of_business","");
			String taxpayerType = internalData.optString("taxpayer_type","");
			String gstInStatus = internalData.optString("gstin_status","");
			String fieldVisitConducted = internalData.optString("field_visit_conducted","");
			String coreBusinessActivityCode = internalData.optString("nature_of_core_business_activity_code","");
			String coreBusinessActivityDescription = internalData
					.optString("nature_of_core_business_activity_description","");
			String aadharValidation = internalData.optString("aadhaar_validation","");
			String aadharValidatedDate = internalData.optString("aadhaar_validation_date","");

			if (fileStatus) {

				JSONArray filingStatusJsonList = internalData.getJSONArray("filing_status");
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			} else {
				JSONArray filingStatusJsonList = new JSONArray();
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			}

			request.setReferenceId(referenceId);
			request.setClientId(clientId);
			request.setCompanyName(businessName);
			request.setAttempt(0);
			request.setStatus("success");

			response.setGstIn(gstIn);
			response.setClientId(clientId);
			response.setPanNumber(panNumber);
			response.setStatus("success");
			response.setReferenceId(referenceId);
			response.setBusinessName(businessName);
			response.setDateOfRegistration(dateOfReg);
			response.setDateOfCancellation(dateOfCancellation);
			response.setAddress(address);

			temporary.setStatus("success");
			temporary.setReferenceId(referenceId);
			temporary.setSource(gstIn);
			temporary.setPanNumber(panNumber);
			temporary.setBusinessName(businessName);
			temporary.setLegalName(legalName);
			temporary.setCenterJurisdiction(centerJurisdiction);
			temporary.setStateJurisdiction(stateJurisdiction);
			temporary.setDateOfRegistration(dateOfReg);
			temporary.setDateOfCancellation(dateOfCancellation);
			temporary.setConstitutionOfBusiness(constitutionOfBusiness);
			temporary.setTaxpayerType(taxpayerType);
			temporary.setGstInStatus(gstInStatus);
			temporary.setFieldVisitConducted(fieldVisitConducted);
			temporary.setCoreBusinessActivityCode(coreBusinessActivityCode);
			temporary.setCoreBusinessActivityDescription(coreBusinessActivityDescription);
			temporary.setAadharValidation(aadharValidation);
			temporary.setAadharValidatedDate(aadharValidatedDate);
			temporary.setAddress(address);

		} else {

			request.setError("error");
			request.setStatus("failed");
			request.setAttempt(model.getAttempt() + 1);
			request.setErrorCode(statusCode);

			response.setErrorCode(statusCode);
			response.setError("error");
			response.setStatus("failed");

			temporary.setStatus("failed");
			temporary.setReferenceId(FileUtils.getRandomOTPnumber(10));
			temporary.setError("Error");
		}

		JSONObject commonResponse = CommonResponseStructure.commonResponseGst(temporary);
		response.setCommonResponse(commonResponse.toString());

		reqRepository.save(request);
		respRepository.save(response);

		// Prepaid Amount Reduction
		if (userModel.getPaymentMethod().getPaymentType().equalsIgnoreCase("Prepaid") && statusCodeNumber != 401 && statusCodeNumber != 403 && !model.isFreeHit()) {

			smartRouteUtils.deductAmountForId(userModel,merchantPriceModel);
		}else if(statusCodeNumber!=401 && statusCodeNumber!=403 && !model.isFreeHit()) {
			
			smartRouteUtils.postpaidConsumedAmount(userModel,merchantPriceModel);
		}

		String encryptedCommonResponse = PasswordUtils.demoEncryption(commonResponse, userModel.getSecretKey());

		Map<String, Object> mapNew = new HashMap<>();
		mapNew.put("return_response", encryptedCommonResponse);

		structure.setStatusCode(HttpStatus.OK.value());
		structure.setFlag(1);
		structure.setData(mapNew);
		structure.setMessage(AppConstants.SUCCESS);

		userRepository.save(userModel);

		return structure;
	}

	private ResponseStructure surepassGstVerification(JSONObject userJson, RequestModel model, EntityModel userModel,
			VendorVerificationModel vendorVerifyModel, VendorModel vendorModel, MerchantPriceModel merchantPriceModel,
			VendorPriceModel vendorPrice) throws Exception {

		ResponseStructure structure = new ResponseStructure();

		String source = userJson.getString("source");
		String sourceType = userJson.getString("source_type");
		boolean filingStatus = userJson.getBoolean("filing_status_get");

		JSONObject obj = new JSONObject();
		obj.put("id_number", source);
		obj.put("filing_status_get", filingStatus);

		Request request = new Request();
		Date reqDate = new Date();

		String surepassResponse = smartRouteUtils.surepassMechanism(obj, merchantPriceModel);

		JSONObject wholeData = new JSONObject(surepassResponse);
		System.err.println("whole Data " + wholeData);

		// VendorReq
		vendorModel.setVendorRequest(vendorModel.getVendorRequest()+1);
		// VendorResponse
		vendorModel.setVendorResponse(vendorModel.getVendorResponse()+1);
		// MonthlyCount
		vendorModel.setMonthlyCount(vendorModel.getMonthlyCount()+1);

		vendorRepository.save(vendorModel);

		int statusCodeNumber = wholeData.getInt("status_code");
		String statusCode = Integer.toString(statusCodeNumber);
		boolean status = wholeData.getBoolean("success");

		smartRouteUtils.errorCodes(statusCodeNumber, vendorModel,userModel,vendorVerifyModel);
		
		String message = null;
		if (status) {
			message = wholeData.optString("message_code","");
		} else {
			message = wholeData.optString("message","");
		}

		Response response = new Response();
		LocalDateTime dateTime = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
		String responseTime = dateTime.format(format);

		String ipAddress = ipAndLocation.publicIpAddress();
		request.setIpAddress(ipAddress);
		response.setIpAddress(ipAddress);

		request.setSource(source);
		request.setFilingStatus(filingStatus);
		request.setSourceType(sourceType);
		request.setRequestBy(model.getRequestBy());
		request.setRequestDateAndTime(reqDate);
		request.setPrice(merchantPriceModel.getIdPrice());
		request.setUser(userModel);
		request.setVerificationModel(vendorVerifyModel);
		request.setResponseDateAndTime(responseTime);
		request.setMessage(message);
		request.setFreeHit(model.isFreeHit());
		request.setConsider(true);

		response.setMessage(message);
		response.setSourceType(sourceType);
		response.setSource(source);
		response.setFilingStatus(filingStatus);
		response.setRequestDateAndTime(new Date());
		response.setRequestBy(model.getRequestBy());
		response.setUser(userModel);
		response.setRequest(request);
		response.setVendorModel(vendorModel);
		response.setResponseDateAndTime(responseTime);
		response.setResponse(surepassResponse);
		response.setVerificationModel(vendorVerifyModel);

		RequestModel temporary = new RequestModel();

		temporary.setResponseDateAndTime(responseTime);
		temporary.setMessage(message);
		temporary.setStatusCodeNumber(statusCodeNumber);
		temporary.setVendorModel(vendorModel);

		if (status) {

			String referenceId = FileUtils.getRandomOTPnumber(10);

			JSONObject internalData = wholeData.getJSONObject("data");

			String clientId = internalData.optString("client_id","");
			String panNumber = internalData.optString("pan_number","");
			String gstIn = internalData.optString("gstin","");
			String businessName = internalData.optString("business_name","");
			String dateOfReg = internalData.optString("date_of_registration","");
			String dateOfCancellation = internalData.optString("date_of_cancellation","");
			String address = internalData.optString("address","");

			String legalName = internalData.optString("legal_name","");
			String centerJurisdiction = internalData.optString("center_jurisdiction","");
			String stateJurisdiction = internalData.optString("state_jurisdiction","");
			String constitutionOfBusiness = internalData.optString("constitution_of_business","");
			String taxpayerType = internalData.optString("taxpayer_type","");
			String gstInStatus = internalData.optString("gstin_status","");
			String fieldVisitConducted = internalData.optString("field_visit_conducted","");
			String coreBusinessActivityCode = internalData.optString("nature_of_core_business_activity_code","");
			String coreBusinessActivityDescription = internalData
					.optString("nature_of_core_business_activity_description","");
			String aadharValidation = internalData.optString("aadhaar_validation","");
			String aadharValidatedDate = internalData.optString("aadhaar_validation_date","");

			if (filingStatus) {

				JSONArray filingStatusJsonList = internalData.getJSONArray("filing_status");
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			} else {
				JSONArray filingStatusJsonList = new JSONArray();
				temporary.setFilingStatusJsonList(filingStatusJsonList);

			}

			request.setReferenceId(referenceId);
			request.setClientId(clientId);
			request.setCompanyName(businessName);
			request.setAttempt(0);
			request.setStatus("success");

			response.setGstIn(gstIn);
			response.setClientId(clientId);
			response.setPanNumber(panNumber);
			response.setStatus("success");
			response.setReferenceId(referenceId);
			response.setBusinessName(businessName);
			response.setDateOfRegistration(dateOfReg);
			response.setDateOfCancellation(dateOfCancellation);
			response.setAddress(address);

			temporary.setStatus("success");
			temporary.setReferenceId(referenceId);
			temporary.setSource(gstIn);
			temporary.setPanNumber(panNumber);
			temporary.setBusinessName(businessName);
			temporary.setLegalName(legalName);
			temporary.setCenterJurisdiction(centerJurisdiction);
			temporary.setStateJurisdiction(stateJurisdiction);
			temporary.setDateOfRegistration(dateOfReg);
			temporary.setDateOfCancellation(dateOfCancellation);
			temporary.setConstitutionOfBusiness(constitutionOfBusiness);
			temporary.setTaxpayerType(taxpayerType);
			temporary.setGstInStatus(gstInStatus);
			temporary.setFieldVisitConducted(fieldVisitConducted);
			temporary.setCoreBusinessActivityCode(coreBusinessActivityCode);
			temporary.setCoreBusinessActivityDescription(coreBusinessActivityDescription);
			temporary.setAadharValidation(aadharValidation);
			temporary.setAadharValidatedDate(aadharValidatedDate);
			temporary.setAddress(address);

		} else {

			request.setError("error");
			request.setStatus("failed");
			request.setAttempt(model.getAttempt() + 1);
			request.setErrorCode(statusCode);

			response.setErrorCode(statusCode);
			response.setError("error");
			response.setStatus("failed");

			temporary.setStatus("failed");
			temporary.setReferenceId(FileUtils.getRandomOTPnumber(10));
			temporary.setError("Error");
		}

		JSONObject commonResponse = CommonResponseStructure.commonResponseGst(temporary);
		response.setCommonResponse(commonResponse.toString());

		reqRepository.save(request);
		respRepository.save(response);

		// Prepaid Amount Reduction
		if (userModel.getPaymentMethod().getPaymentType().equalsIgnoreCase("Prepaid") && statusCodeNumber != 401 && statusCodeNumber != 403 && !model.isFreeHit()) {

			smartRouteUtils.deductAmountForId(userModel,merchantPriceModel);
		}else if(statusCodeNumber!=401 && statusCodeNumber!=403 && !model.isFreeHit()) {
			
			smartRouteUtils.postpaidConsumedAmount(userModel,merchantPriceModel);
		}

		String encryptedCommonResponse = PasswordUtils.demoEncryption(commonResponse, userModel.getSecretKey());

		Map<String, Object> mapNew = new HashMap<>();
		mapNew.put("return_response", encryptedCommonResponse);

		structure.setStatusCode(HttpStatus.OK.value());
		structure.setFlag(1);
		structure.setData(mapNew);
		structure.setMessage(AppConstants.SUCCESS);

		userRepository.save(userModel);

		return structure;

	}
}
