package com.bp.middleware.vendorapipricesetup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.bp.middleware.vendors.VendorModel;
import com.bp.middleware.vendors.VendorVerificationModel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name="vendor_price_model")
public class VendorPriceModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="vendor_price_id")
	private int vendorPriceId;
	
	@Column(name="api_link")
	private String apiLink;
	@Column(name="name")
	private String name;
	@Column(name="id_price")
	private double idPrice;
	@Column(name="signature_price")
	private double signaturePrice;
	@Column(name="image_price")
	private double imagePrice;
	@Column(name="status")
	private boolean status;
	@Column(name="created_by")
	private String createdBy;
	@Column(name="created_at")
	private LocalDate createdAt;
	@Column(name="modified_by")
	private String modifiedBy;
	@Column(name="modified_at")
	private LocalDate modifiedAt;
	
	@Column(name="application_id")
	private String applicationId;
	@Column(name="api_key")
	private String apiKey;
	
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_id")
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	private VendorModel vendorModel;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendor_verification_id")
	@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
	private VendorVerificationModel vendorVerificationModel;

	
	
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getVendorPriceId() {
		return vendorPriceId;
	}

	public void setVendorPriceId(int vendorPriceId) {
		this.vendorPriceId = vendorPriceId;
	}

	public String getApiLink() {
		return apiLink;
	}

	public void setApiLink(String apiLink) {
		this.apiLink = apiLink;
	}



	public double getIdPrice() {
		return idPrice;
	}

	public void setIdPrice(double idPrice) {
		this.idPrice = twoDecimelDouble(idPrice);
	}

	public double getImagePrice() {
		return imagePrice;
	}

	public void setImagePrice(double imagePrice) {
		this.imagePrice = twoDecimelDouble(imagePrice);
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public boolean isStatus() {
		return status;
	}

	public void setStatus(boolean status) {
		this.status = status;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public LocalDate getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDate createdAt) {
		this.createdAt = createdAt;
	}

	public String getModifiedBy() {
		return modifiedBy;
	}

	public void setModifiedBy(String modifiedBy) {
		this.modifiedBy = modifiedBy;
	}

	public LocalDate getModifiedAt() {
		return modifiedAt;
	}

	public void setModifiedAt(LocalDate modifiedAt) {
		this.modifiedAt = modifiedAt;
	}

	public VendorModel getVendorModel() {
		return vendorModel;
	}

	public void setVendorModel(VendorModel vendorModel) {
		this.vendorModel = vendorModel;
	}

	public VendorVerificationModel getVendorVerificationModel() {
		return vendorVerificationModel;
	}

	public void setVendorVerificationModel(VendorVerificationModel vendorVerificationModel) {
		this.vendorVerificationModel = vendorVerificationModel;
	}

	public double getSignaturePrice() {
		return signaturePrice;
	}

	public void setSignaturePrice(double signaturePrice) {
		this.signaturePrice = twoDecimelDouble(signaturePrice);
	}

	
	
	
	
	
	private double twoDecimelDouble(double value) {
		
		BigDecimal bd = BigDecimal.valueOf(value);
		bd = bd.setScale(2, RoundingMode.HALF_UP); // Rounds to 2 decimal places

		double roundedValue = bd.doubleValue();
		
		return roundedValue;
	}
}
