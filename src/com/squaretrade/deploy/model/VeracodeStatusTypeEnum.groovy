package com.squaretrade.deploy.model

/**
 * Veracode status type
 */
enum VeracodeStatusTypeEnum {

	PRESCAN_SUBMITTED('Pre-Scan Submitted'), 
    RESULTS_READY('Results Ready')

	private final String status
	
	VeracodeStatusTypeEnum (String status) {
	    this.status = status
	}

	String getStatusValue() {
		status
	}

    public String toString() {
        return status.toString()
    }

}
