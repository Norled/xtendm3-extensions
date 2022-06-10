/***************************************************************************************************
 *
 * Business Engine Extension
 *
 ***************************************************************************************************
 * Extension Name: EXT270MI.UpdBankOp
 * Type: Transaction
 * Description: This extension is being used to update bank operation in M3 db-table OPSALE
 *
 * Date         Changed By              Version             Description
 * 20220602     Frank Herman Wik        1.0                 Initial Release
 *
 **************************************************************************************************/
public class UpdBankOp extends ExtendM3Transaction {
  private final MIAPI mi
  private final DatabaseAPI database
  private final UtilityAPI utility
  private final ProgramAPI program
  private final LoggerAPI logger

  public UpdBankOp(MIAPI mi, DatabaseAPI databaseAPI, ProgramAPI program, UtilityAPI util, LoggerAPI logger) {
    this.mi = mi
    this.database = databaseAPI
    this.utility = util
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

    // Update OPSALE record
    updateBankOperation(parameters)
  }

  /**
   * Performs an update of M3 table OPSALE
   * <p>
   * This method update M3 table OPSALE.
   * If the update record was unsuccesful, an MI error message will be shown to the user
   *
   * @param  parameters  A hashmap containing all input parameters for EXT270MI.UpdBankOp
   * @return void
   */
  private void updateBankOperation(Map<String, String> parameters) {
    // Update "bank operation" in table OPSALE
    DBAction query = database.table("OPSALE").index("00").selection("OPCONO","OPDIVI","OPORNO","OPDLIX","OPPONR","OPPOSX","OPWHLO","OPBOPC","OPLMDT","OPCHNO","OPCHID").build()
    DBContainer container = query.getContainer()

    container.set("OPCONO", Integer.parseInt(parameters.get("CONO")))
    container.set("OPDIVI", parameters.get("DIVI"))
    container.set("OPORNO", parameters.get("ORNO"))
    container.set("OPDLIX", Long.parseLong(parameters.get("DLIX")))
    container.set("OPPONR", Integer.parseInt(parameters.get("PONR")))
    container.set("OPPOSX", Integer.parseInt(parameters.get("POSX")))
    container.set("OPWHLO", parameters.get("WHLO"))

    logger.debug("updateBankOperation BOPC = " + parameters.get("BOPC"))

    Closure<?> updateCallBack = { LockedResult lockedResult ->
      lockedResult.set("OPBOPC", parameters.get("BOPC"))
      lockedResult.set("OPLMDT", Integer.parseInt(utility.call("DateUtil","currentDateY8AsString").toString() ))
      lockedResult.set("OPCHNO", lockedResult.getInt("OPCHNO") + 1)
      lockedResult.set("OPCHID", program.getUser())
      lockedResult.update()
    }

    if (!query.readLock(container, updateCallBack)) {
      mi.error("Could not update record in table OPSALE")
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
    String orno // Customer order number (mandatory)
    String dlix // Delivery number (mandatory)
    String ponr // Order line number (mandatory)
    String posx // Line suffix (mandatory)
    String whlo // Warehouse (mandatory)
    String bopc // Bank operation

    // Company
    if (mi.inData.get("CONO") == null || mi.inData.get("CONO").trim() == "") {
      cono = String.valueOf((Integer)program.getLDAZD().CONO)
    } else {
      try {
        cono = String.valueOf(Integer.parseInt(mi.inData.get("CONO").trim()))
      } catch (NumberFormatException e) {
        mi.error("Company must be numerical", "CONO", "01")
        return false
      }
    }

    // Division
    divi = mi.inData.get("DIVI") == null ? "" : mi.inData.get("DIVI").trim()
    if (divi == "") {
      mi.error("Division must be entered", "DIVI", "01")
      return false
    }

    // Validate division
    if (!checkDivi(cono, divi)) {
      return false
    }

    // Customer order number
    orno = mi.inData.get("ORNO") == null ? "" : mi.inData.get("ORNO").trim()
    if (orno == "") {
      mi.error("Customer order number must be entered", "ORNO", "01")
      return false
    }

    dlix = mi.inData.get("DLIX") == null ? "" : mi.inData.get("DLIX").trim()
    dlix = dlix.equals("?") ? "" : dlix
    if (dlix.equals("")) {
      mi.error("Delivery number must be filled in", "DLIX", "01")
    }

    // Order line number
    ponr = mi.inData.get("PONR") == null ? "" : mi.inData.get("PONR").trim()
    if (ponr == "") {
      mi.error("Order line number must be entered", "PONR", "01")
    } else {
      try {
        String.valueOf(Integer.parseInt(ponr))
      } catch (NumberFormatException e) {
        mi.error("Order line number must be numerical", "PONR", "01")
        return false
      }
    }

    // Line suffix
    posx = mi.inData.get("POSX") == null ? "" : mi.inData.get("POSX").trim()
    if (posx == "") {
      mi.error("Order line suffix must be entered", "POSX", "01")
    } else {
      try {
        String.valueOf(Integer.parseInt(posx))
      } catch (NumberFormatException e) {
        mi.error("Order line suffix must be numerical", "POSX", "01")
        return false
      }
    }

    whlo = mi.inData.get("WHLO") == null ? "" : mi.inData.get("WHLO").trim()
    // Warehouse
    if (whlo == "") {
      mi.error("Warehouse must be entered", "WHLO", "01")
      return false
    }

    // Validate Warehouse
    if (!warehouseExists(Integer.parseInt(cono), whlo)) {
      return false
    }

    // Bank operation
    bopc = mi.inData.get("BOPC") == null ? "" : mi.inData.get("BOPC").trim()
    if (bopc != "") {
      // Validate input value "Bank operation"
      if (!isValidSysTabVal(cono, "", "BOPC", bopc, "")) {
        mi.error("Bank operation ${bopc} does not exist")
        return false
      }
    }

    // Store input parameters in <parameters> hashmap
    parameters.put("CONO", cono)
    parameters.put("DIVI", divi)
    parameters.put("ORNO", orno)
    parameters.put("DLIX", dlix)
    parameters.put("PONR", ponr)
    parameters.put("POSX", posx)
    parameters.put("WHLO", whlo)
    parameters.put("BOPC", bopc)

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
   * Checks if the input warehouse exists within the given M3 company
   * <p>
   * This method checks M3 table MITWHL using the input CONO and WHLO (using index 00) to verify
   * if the input WHLO occurs in M3. If that is the case, the function returns true.
   * If not, it will return false and set an MI error message.
   *
   * @param  cono The M3 company for which MITWHL should be checked
   * @param  whlo The warehouse that should be checked
   * @return      true if the warehouse was found in MITWHL, false if not
   */
  private boolean warehouseExists(int cono, String whlo) {
    // Check if warehouse exists in M3
    boolean result = false
    DBAction query = database.table("MITWHL").index("00").selection("MWCONO", "MWWHLO", "MWDIVI", "MWFACI").build()

    DBContainer container = query.getContainer()

    container.set("MWCONO", cono)
    container.set("MWWHLO", whlo)

    result = query.read(container)

    return result
  }

  /**
   * Validate if the input "Bank operation" (CRS079) exists in CSYTAB within the given M3 company
   *
   *  @param  company     Company
   *  @param  division    Division
   *  @param  constantVal Constant value
   *  @param  keyVal      Key value
   *  @param  language    Language
   *  @return      true if Bank operation was found in CSYTAB, false if not
   **/
  private boolean isValidSysTabVal(String company, String division, String constantVal, String keyVal, String language){
    DBAction query = database.table("CSYTAB").index("00").build()
    DBContainer container = query.createContainer()

    container.set("CTCONO", Integer.parseInt(company))
    container.set("CTDIVI", division)
    container.set("CTSTCO", constantVal)
    container.set("CTSTKY", keyVal)
    container.set("CTLNCD", language)

    if(query.read(container)) {
      return true
    } else {
      return false
    }
  }
}
