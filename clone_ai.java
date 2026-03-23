/**
 * Creates an XSSFSheet from an existing sheet in the XSSFWorkbook.
 * The cloned sheet is a deep copy of the original but with a new given name.
 *
 * @param sheetNum The index of the sheet to clone
 * @param newName  The name to set for the newly created sheet (if null, a unique name is generated)
 * @return XSSFSheet representing the cloned sheet.
 * @throws IllegalArgumentException if the sheet index or the sheet name is invalid
 * @throws POIXMLException          if there were errors when cloning
 */
public XSSFSheet cloneSheet(int sheetNum, String newName) {
    validateSheetIndex(sheetNum);
    XSSFSheet srcSheet = sheets.get(sheetNum);

    String resolvedSheetName = resolveAndValidateSheetName(srcSheet, newName);
    XSSFSheet clonedSheet = createSheet(resolvedSheetName);

    XSSFDrawing drawingToClone = copyInternalRelationships(srcSheet, clonedSheet);
    copyExternalRelationships(srcSheet, clonedSheet);
    
    deepCopySheetData(srcSheet, clonedSheet);
    
    sanitizeUnsupportedFeatures(clonedSheet);
    clonedSheet.setSelected(false);

    if (drawingToClone != null) {
        cloneSheetDrawings(srcSheet, clonedSheet, drawingToClone);
    }

    return clonedSheet;
}

/**
 * Resolves the new sheet name, generating a unique one if null is provided, 
 * and validates the final name.
 */
private String resolveAndValidateSheetName(XSSFSheet srcSheet, String newName) {
    if (newName == null) {
        return getUniqueSheetName(srcSheet.getSheetName());
    }
    validateSheetName(newName);
    return newName;
}

/**
 * Copies internal relation parts from the source sheet to the cloned sheet.
 * Extracts and returns the XSSFDrawing if found, as it requires special handling later.
 * * @return The XSSFDrawing from the source sheet, or null if none exists.
 */
private XSSFDrawing copyInternalRelationships(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    XSSFDrawing sourceDrawing = null;
    
    for (RelationPart rp : srcSheet.getRelationParts()) {
        POIXMLDocumentPart documentPart = rp.getDocumentPart();
        
        // Defer drawing relationship copying; it must be completely recreated.
        if (documentPart instanceof XSSFDrawing) {
            sourceDrawing = (XSSFDrawing) documentPart;
            continue;
        }
        addRelation(rp, clonedSheet);
    }
    
    return sourceDrawing;
}

/**
 * Copies all external package relationships (e.g., hyperlinks) from the source sheet.
 */
private void copyExternalRelationships(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try {
        for (PackageRelationship pr : srcSheet.getPackagePart().getRelationships()) {
            if (pr.getTargetMode() == TargetMode.EXTERNAL) {
                clonedSheet.getPackagePart().addExternalRelationship(
                        pr.getTargetURI().toASCIIString(), 
                        pr.getRelationshipType(), 
                        pr.getId()
                );
            }
        }
    } catch (InvalidFormatException e) {
        throw new POIXMLException("Failed to clone sheet: Error copying external relationships", e);
    }
}

/**
 * Performs a deep copy of the sheet's underlying XML data using byte streams.
 */
private void deepCopySheetData(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        srcSheet.write(out);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(out.toByteArray())) {
            clonedSheet.read(bis);
        }
    } catch (IOException e) {
        throw new POIXMLException("Failed to clone sheet: Error reading/writing sheet data streams", e);
    }
}

/**
 * Removes features from the cloned sheet that POI does not yet support during cloning.
 */
private void sanitizeUnsupportedFeatures(XSSFSheet clonedSheet) {
    CTWorksheet ctWorksheet = clonedSheet.getCTWorksheet();
    
    if (ctWorksheet.isSetLegacyDrawing()) {
        logger.log(POILogger.WARN, "Cloning sheets with comments is not yet supported.");
        ctWorksheet.unsetLegacyDrawing();
    }
    
    if (ctWorksheet.isSetPageSetup()) {
        logger.log(POILogger.WARN, "Cloning sheets with page setup is not yet supported.");
        ctWorksheet.unsetPageSetup();
    }
}

/**
 * Deep clones the drawing patriarch and its associated relationships.
 */
private void cloneSheetDrawings(XSSFSheet srcSheet, XSSFSheet clonedSheet, XSSFDrawing srcDrawing) {
    CTWorksheet ctWorksheet = clonedSheet.getCTWorksheet();
    
    // Unset the existing reference so a clean one is generated
    if (ctWorksheet.isSetDrawing()) {
        ctWorksheet.unsetDrawing();
    }

    XSSFDrawing clonedDrawing = clonedSheet.createDrawingPatriarch();
    clonedDrawing.getCTDrawing().set(srcDrawing.getCTDrawing());

    // Note: Re-fetching the patriarch is required in this POI version to re-bind the newly set CTDrawing data.
    clonedDrawing = clonedSheet.createDrawingPatriarch();

    // Clone the relationships specifically tied to the drawing
    List<RelationPart> srcDrawingRels = srcSheet.createDrawingPatriarch().getRelationParts();
    for (RelationPart rp : srcDrawingRels) {
        addRelation(rp, clonedDrawing);
    }
}