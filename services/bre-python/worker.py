import os
import json
import logging
from datetime import datetime
from flask import Flask, request, jsonify

# Setup structured logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)

def extract_verified_income(document_extractions):
    """Extract verified income from pre-extracted document data"""
    verified_income = 0
    confidence_score = 0
    income_source = "NONE"

    try:
        logger.info(f"Starting income extraction from document extractions")
        
        salary_slip_income = 0
        itr_income = 0
        bank_statement_income = 0

        # Extract salary slip data
        salary_slip_data = document_extractions.get('salarySlip', {})
        if salary_slip_data:
            logger.info(f"Processing salary slip data: {salary_slip_data}")
            net_salary = salary_slip_data.get('netSalary')
            gross_salary = salary_slip_data.get('grossSalary')
            
            if net_salary and net_salary > 0:
                salary_slip_income = net_salary
                logger.info(f"Found salary slip net income: {net_salary}")
            elif gross_salary and gross_salary > 0:
                salary_slip_income = gross_salary * 0.8  # Assuming 20% deductions
                logger.info(f"Found salary slip gross income: {gross_salary}, using {gross_salary * 0.8}")

        # Extract ITR data
        itr_data = document_extractions.get('incomeTaxReturn', {})
        if itr_data:
            logger.info(f"Processing ITR data: {itr_data}")
            annual_income = itr_data.get('grossTotalIncome') or itr_data.get('totalGrossIncome') or itr_data.get('taxableIncome')
            if annual_income and annual_income > 0:
                monthly_income = annual_income / 12
                itr_income = monthly_income
                logger.info(f"Found ITR annual income: {annual_income}, monthly: {monthly_income}")

        # Extract bank statement data
        bank_statement_data = document_extractions.get('bankStatement', {})
        if bank_statement_data:
            logger.info(f"Processing bank statement data: {bank_statement_data}")
            
            # Method 1: Average balance as income proxy (as requested)
            opening_balance = bank_statement_data.get('openingBalance')
            closing_balance = bank_statement_data.get('closingBalance')
            
            if opening_balance is not None and closing_balance is not None:
                average_balance = (opening_balance + closing_balance) / 2
                bank_statement_income = average_balance
                logger.info(f"Using average balance as income: opening={opening_balance}, closing={closing_balance}, average={average_balance}")
            # Method 2: Total credits (original method as fallback)
            elif bank_statement_data.get('totalCredits') is not None:
                total_credits = bank_statement_data.get('totalCredits')
                statement_period_months = 3  # Assume 3 months
                bank_statement_income = total_credits / statement_period_months
                logger.info(f"Using total credits as income: credits={total_credits}, monthly={bank_statement_income}")

        # Use highest confidence income source
        if salary_slip_income > 0:
            verified_income = salary_slip_income
            confidence_score = 80
            income_source = "SALARY_SLIP"
        elif itr_income > 0:
            verified_income = itr_income
            confidence_score = 75
            income_source = "INCOME_TAX_RETURN"
        elif bank_statement_income > 0:
            verified_income = bank_statement_income
            confidence_score = 60
            income_source = "BANK_STATEMENT"

        # Boost confidence if multiple sources agree
        income_sources = sum(i > 0 for i in [salary_slip_income, itr_income, bank_statement_income])
        if income_sources > 1:
            confidence_score += 10

        logger.info(f"Final verified income: {verified_income}, Source: {income_source}, Confidence: {confidence_score}")
        return verified_income, confidence_score, income_source
    except Exception as e:
        logger.error(f"Error extracting verified income: {e}", exc_info=True)
        return 0, 0, "ERROR"

def calculate_loan_decision(applicant_data, verified_income=0, declared_income=0, emi_obligations=0, credit_score=0):
    """Calculate loan decision based on rules"""
    # Default to REFER_TO_UNDERWRITER
    decision = "REFER_TO_UNDERWRITER"
    reasons = []
    
    # Use verified income if available, otherwise fall back to declared
    income_to_use = verified_income if verified_income > 0 else declared_income
    
    # Extract relevant data
    personal_info = applicant_data.get('personalInfo', {})
    bureau_data = applicant_data.get('bureauData', {})
    
    # Get credit score
    if credit_score <= 0:
        credit_score = bureau_data.get('creditScore', 0)
    
    # Check for negative bureau flags
    has_default = bureau_data.get('writtenOffStatus', False) or bureau_data.get('suitFiled', False)
    
    # Calculate age
    age = 30  # Default age
    try:
        dob_str = personal_info.get('dateOfBirth')
        if dob_str:
            dob = datetime.strptime(dob_str, "%Y-%m-%d").date()
            today = datetime.today().date()
            age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
    except Exception as e:
        logger.warning(f"Invalid DOB format: {e}")
    
    # Calculate DTI ratio (if income present)
    dti_ratio = (emi_obligations / income_to_use) if income_to_use > 0 else 1.0
    
    # Apply auto-rejection rules
    if age < 21 or age > 60:
        decision = "REJECTED"
        reasons.append("AGE_OUT_OF_RANGE")
    
    if has_default:
        decision = "REJECTED"
        reasons.append("NEGATIVE_BUREAU_HISTORY")
    
    if income_to_use < 15000:
        decision = "REJECTED"
        reasons.append("INCOME_BELOW_MINIMUM")
    
    if credit_score > 0 and credit_score < 600:
        decision = "REJECTED"
        reasons.append("LOW_CREDIT_SCORE")
    
    if dti_ratio > 0.7:
        decision = "REJECTED"
        reasons.append("HIGH_DTI_RATIO")
    
    # If not auto-rejected, check for auto-approval
    if decision != "REJECTED":
        if verified_income >= 25000 and credit_score >= 650 and dti_ratio < 0.7 and not has_default:
            decision = "AUTO_APPROVED"
        else:
            # Add reasons for referral
            if verified_income == 0:
                reasons.append("NO_VERIFIED_INCOME")
            if 0.6 <= dti_ratio <= 0.7:
                reasons.append("BORDERLINE_DTI")
            if 600 <= credit_score < 650:
                reasons.append("BORDERLINE_CREDIT_SCORE")
    
    return decision, reasons

def calculate_loan_offer(verified_income, declared_income, current_emi, credit_score):
    """Calculate loan offer based on income and credit profile"""
    # Use verified income if available, otherwise declared
    income_to_use = verified_income if verified_income > 0 else declared_income
    
    # Calculate maximum EMI the user can afford (80% of income - current EMI)
    income_ceiling = income_to_use * 0.8
    max_emi_for_new_loan = max(0, income_ceiling - current_emi)
    
    # Determine interest rate based on credit score
    if credit_score >= 750:
        interest_rate = 12.0
    elif credit_score >= 650:
        interest_rate = 14.0
    else:
        interest_rate = 16.0
    
    # Set max tenure
    max_tenure = 60
    min_tenure = 12
    
    # Calculate loan amount using EMI formula (present value of annuity)
    monthly_rate = interest_rate / (12 * 100)
    
    if monthly_rate > 0 and max_emi_for_new_loan > 0:
        loan_amount = max_emi_for_new_loan * (1 - (1 + monthly_rate) ** -max_tenure) / monthly_rate
    else:
        loan_amount = max_emi_for_new_loan * max_tenure
    
    # Round to nearest thousand
    loan_amount = round(loan_amount / 1000) * 1000
    
    # Calculate min and max amounts
    min_amount = round(max(100000, loan_amount * 0.5) / 1000) * 1000
    max_amount = round(min(income_to_use * 36, loan_amount * 1.5) / 1000) * 1000
    
    # Ensure max is at least min + 50000
    if max_amount < min_amount + 50000:
        max_amount = min_amount + 50000
    
    return {
        "approvedLoanAmount": float(loan_amount),
        "minLoanAmount": float(min_amount),
        "maxLoanAmount": float(max_amount),
        "interestRate": float(interest_rate),
        "minTenure": min_tenure,
        "maxTenure": max_tenure,
        "processingFeePercentage": 1.0
    }

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({"status": "healthy"}), 200

@app.route('/calculate-offer', methods=['POST'])
def calculate_offer():
    """Main endpoint to calculate loan offers"""
    try:
        request_data = request.json
        application_id = request_data.get('applicationId')

        if not application_id:
            return jsonify({"error": "applicationId is required"}), 400

        logger.info(f"Processing calculate-offer request for application: {application_id}")
        
        # Extract key data
        personal_info = request_data.get('personalInfo', {})
        employment_details = request_data.get('employmentDetails', {})
        bureau_data = request_data.get('bureauData', {})
        document_extractions = request_data.get('documentExtractions', {})
        
        # Log important input data
        logger.info(f"Application ID: {application_id}")
        logger.info(f"Monthly Salary: {employment_details.get('monthlySalary')}")
        logger.info(f"Credit Score: {bureau_data.get('creditScore')}")
        logger.info(f"Document Extractions Keys: {list(document_extractions.keys())}")
        
        # Extract declared income and EMI
        declared_income = employment_details.get('monthlySalary', 0)
        current_emi = employment_details.get('monthlyEmi', 0)
        
        # Check for recalculated obligation from LLM
        llm_recalculated_obligation = request_data.get('llmRecalculatedObligation')
        if llm_recalculated_obligation is not None:
            current_emi = llm_recalculated_obligation
            logger.info(f"Using LLM recalculated obligation: {current_emi}")
        
        # Extract bureau score
        credit_score = bureau_data.get('creditScore', 0)
        
        # Get verified income from document extractions
        verified_income, confidence_score, income_source = extract_verified_income(document_extractions)
        
        # Calculate loan decision
        decision, reasons = calculate_loan_decision(
            request_data, verified_income, declared_income, current_emi, credit_score
        )
        
        # Calculate loan offer 
        loan_offer = calculate_loan_offer(verified_income, declared_income, current_emi, credit_score)
        
        # Construct BRE output
        bre_output = {
            "applicationId": application_id,
            "decisionStatus": decision,
            "rejectionReasons": reasons if decision == "REJECTED" else [],
            "referralReasons": reasons if decision == "REFER_TO_UNDERWRITER" else [],
            "verifiedIncome": float(verified_income),
            "incomeVerificationConfidence": confidence_score,
            "incomeVerificationSource": income_source,
            "timestamp": datetime.now().isoformat(),
            "ruleVersion": "2.0",
            **loan_offer
        }
        
        logger.info(f"BRE output for {application_id}: decision={decision}, verified_income={verified_income}")
        logger.info(f"Loan offer details: amount={loan_offer['approvedLoanAmount']}, interest={loan_offer['interestRate']}%")
        
        if decision == "REFER_TO_UNDERWRITER":
            logger.info(f"Referral reasons: {reasons}")
        
        return jsonify(bre_output), 200

    except Exception as e:
        logger.error(f"Error processing request: {e}", exc_info=True)
        return jsonify({
            "error": str(e),
            "decisionStatus": "REFER_TO_UNDERWRITER",
            "referralReasons": ["INTERNAL_ERROR"]
        }), 500

if __name__ == "__main__":
    port = int(os.environ.get('PORT', 8080))
    app.run(host="0.0.0.0", port=port)