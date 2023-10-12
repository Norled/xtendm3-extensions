/***************************************************************************************************
 *
 * Business Engine Extension
 *
 ***************************************************************************************************
 * Extension Name: EXT270MI.DelSlsTicket
 * Type: Transaction
 * Description: This extension is being used to delete record(s) in M3 db-table OPSALE
 *
 * Date         Changed By              Version             Description
 * 20231010     Frank Herman Wik        1.0                 Initial Release
 *
 **************************************************************************************************/
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

public class DelSlsTicket extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final ProgramAPI program
  private final LoggerAPI logger

  private int nrOfDeleted = 0

  public DelSlsTicket(MIAPI mi, DatabaseAPI databaseAPI, ProgramAPI program, LoggerAPI logger) {
    this.mi = mi
    this.database = databaseAPI
    this.program = program
    this.logger = logger
  }

  public void main() {

    // Hashmap used to store API input other parameters.
    Map<String, String> parameters = new HashMap<String, String>()

    // Retrieve api input parameters, and do initial checks on data
    if (!retrieveInput(parameters)) {
      return
    }

    // Delete OPSALE record(s)
    deleteSalesTicket(parameters)
  }

  /**
   * Delete record(s) in M3 table OPSALE
   * <p>
   * This method delete record(s) in M3 table OPSALE.
   * If the delete was unsuccessful, an MI error message will be shown to the user
   *
   * @param  parameters  A hashmap containing all input parameters for EXT270MI.DelSlsTicket
   * @return void
   */
  private void deleteSalesTicket(Map<String, String> parameters) {
    ExpressionFactory exp = database.getExpressionFactory("OPSALE")
    exp = exp.eq("OPVONO", parameters.get("VONO"))

    DBAction query = database.table("OPSALE").index("62").matching(exp).build()
    DBContainer container = query.getContainer()

    container.set("OPCONO", Integer.parseInt(parameters.get("CONO")))
    container.set("OPDIVI", parameters.get("DIVI"))
    container.set("OPTRDT", Integer.parseInt(parameters.get("TRDT")))

    Closure<?> lockedResultHandler = { LockedResult data ->
      data.delete()
      nrOfDeleted = nrOfDeleted + 1
    }

    if (!query.readAllLock(container, 3, lockedResultHandler)) {
      mi.error("Could not lock and delete record(s) in table OPSALE")
    } else {
      mi.outData.put("NRDE", nrOfDeleted.toString())
      mi.write()
    }
  }

  /**
   * Retrieves and validates API input parameters. Return true if all input is valid, and false if it encounters invalid input.
   * <p>
   * This method retrieves the API input parameters, and validates them. Checks are made to see
   * if mandatory fields have been filled in, and if fields are in the correct format. All input
   * parameters are stored in the parameters hashmap passed as input to this method. This method
   * returns true if all input fields are valid, and false if an invalid field was encountered.
   *
   * @param    parameters  A hashmap in which to store all API input parameters
   * @return   boolean     true if all input API input fields are valid, false if not
   */
  private boolean retrieveInput(Map<String, String> parameters) {
    String cono // Company
    String divi // Division (mandatory)
    String trdt // Transaction date (mandatory)
    String vono // Voucher number (mandatory)

    // Company
    if (mi.inData.get("CONO") == null || mi.inData.get("CONO").trim() == "") {
      cono = String.valueOf((Integer)program.getLDAZD().CONO)
    } else {
      try {
        cono = String.valueOf(Integer.parseInt(mi.inData.get("CONO").trim()))
      } catch (NumberFormatException e) {
        mi.error("Company must be numerical")
        return false
      }
    }

    // Division
    divi = mi.inData.get("DIVI") == null ? "" : mi.inData.get("DIVI").trim()
    if (divi == "") {
      mi.error("Division must be entered")
      return false
    }

    // Validate division
    if (!checkDivi(cono, divi)) {
      return false
    }

    // Transaction date
    trdt = mi.inData.get("TRDT") == null ? "" : mi.inData.get("TRDT").trim()
    if (trdt == "") {
      mi.error("Transaction date must be entered")
      return false
    } else {
      // - Validate transaction date
      if (!isValidDate(trdt, "yyyyMMdd")) {
        mi.error("Transaction date ${trdt} is invalid")
        return
      }
    }

    // Voucher number
    vono = mi.inData.get("VONO") == null ? "" : mi.inData.get("VONO").toString().trim()
    if (vono == "") {
      mi.error("Voucher number must be entered")
      return false
    } else {
      try {
        String.valueOf(Integer.parseInt(vono))
      } catch (NumberFormatException e) {
        mi.error("Voucher number must be numerical")
        return false
      }
    }

    // Store input parameters in <parameters> hashmap
    parameters.put("CONO", cono)
    parameters.put("DIVI", divi)
    parameters.put("TRDT", trdt)
    parameters.put("VONO", vono)

    logger.debug("Input parameters: " + parameters)

    return true
  }

  /**
   * Checks if the input division exists within the given M3 company
   * <p>
   * This method checks M3 table CMNDIV using the input CONO and DIVI,
   * to verify if the input supplier occurs in M3. If that is the case, the function
   * returns true. If not, it will return false and set an MI error
   * message.
   *
   * @param    cono    the company number used to check CIDMAS
   * @param    divi    the division number to check in CMNDIV
   * @return   true if the division was found in CMNDIV, false if not
   */
  private boolean checkDivi(String cono, String divi) {
    // Check if division exists in M3
    DBAction query = database.table("CMNDIV").index("00").selection("CCCONO", "CCDIVI").build()
    DBContainer container = query.getContainer()

    container.set("CCCONO", Integer.parseInt(cono))
    container.set("CCDIVI", divi)

    if (!query.read(container)) {
      mi.error("Division ${divi} does not exist")
      return false
    } else {
      return true
    }
  }

  /**
   * Check if date is valid
   *
   * @param   value   input date
   * @param   pattern input dateformat
   * @return  boolean   true if date is valid, false if not
   **/
  private boolean isValidDate(String value, String pattern) {
    try {
      LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern))
      return true
    } catch (DateTimeParseException e) {
      return false
    }
  }
}
