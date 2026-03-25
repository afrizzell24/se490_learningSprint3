import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Create an XSSFSheet from an existing sheet in the XSSFWorkbook.
 * The cloned sheet is a deep copy of the original but with a new given
 * name.
 *
 * @param sheetNum The index of the sheet to clone
 * @param newName  The name to set for the newly created sheet
 * @return XSSFSheet representing the cloned sheet.
 * @throws IllegalArgumentException if the sheet index or the sheet
 *                                  name is invalid
 * @throws POIXMLException          if there were errors when cloning
 */
public XSSFSheet cloneSheet(int sheetNum, String newName) {

    // Get source sheet
    validateSheetIndex(sheetNum);
    XSSFSheet srcSheet = sheets.get(sheetNum);

    // Create a new sheet
    String newValidName = getNewValidSheetName(newName);
    XSSFSheet clonedSheet = createSheet(newName);

    // Copy relations and data to the new sheet
    XSSFDrawing drawing = cloneSheetRelationsExceptDrawing(srcSheet, clonedSheet);
    cloneSheetData(srcSheet, clonedSheet);

    // Check for unsupported features
    CTWorksheet ct = clonedSheet.getCTWorksheet();
    unsetUnsupportedFeatures(ct);

    clonedSheet.setSelected(false);

    // Clone the sheet drawing
    if (drawing != null) {
        cloneDrawing(ct, drawing, srcSheet, clonedSheet);
    }

    return clonedSheet;
}

/**
 * Validates that the new name or creates a new unique name if it is null
 * 
 * @param newName
 * @return The new, valid sheet name
 */
private String getNewValidSheetName(String newName) {
    if (newName == null) {
        String srcName = srcSheet.getSheetName();
        newName = getUniqueSheetName(srcName);
    } else {
        validateSheetName(newName);
    }

    return newName;
}

/**
 * Copies relations from the old sheet to the new sheet. Skips the drawing
 * relationship which will be re-created
 * 
 * @param srcSheet
 * @param clonedSheet
 * @return the sheet drawing to be cloned later
 */
private XSSFDrawing cloneSheetRelationsExceptDrawing(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    List<RelationPart> rels = srcSheet.getRelationParts();
    // Copy all relations except for the drawing relation
    XSSFDrawing drawing = null;
    for (RelationPart rp : rels) {
        POIXMLDocumentPart r = rp.getDocumentPart();
        // do not copy the drawing relationship, it will be re-created from the existing
        // drawing object
        if (r instanceof XSSFDrawing) {
            drawing = (XSSFDrawing) r;
            continue;
        }

        addRelation(rp, clonedSheet);
    }

    cloneExternalRelations(srcSheet, clonedSheet);
    return drawing;
}

/**
 * Copies external relations from the old sheet to the new sheet
 * 
 * @param srcSheet
 * @param clonedSheet
 */
private void cloneExternalRelations(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try {
        for (PackageRelationship pr : srcSheet.getPackagePart().getRelationships()) {
            if (pr.getTargetMode() == TargetMode.EXTERNAL) {
                clonedSheet.getPackagePart().addExternalRelationship(
                        pr.getTargetURI().toASCIIString(),
                        pr.getRelationshipType(),
                        pr.getId());
            }
        }
    } catch (InvalidFormatException e) {
        throw new POIXMLException("Failed to clone sheet external relations", e);
    }
}

/**
 * Copies data from the old sheet to the new sheet
 * 
 * @param srcSheet
 * @param clonedSheet
 */
private void cloneSheetData(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try (ByteArrayOutputStream src_out = new ByteArrayOutputStream()) {
        // Write the old sheet data to a strem
        srcSheet.write(src_out);
        try (ByteArrayInputStream src_in = new ByteArrayInputStream(src_out.toByteArray())) {
            // Read that data from the stream to the new sheet
            clonedSheet.read(src_in);
        }
    } catch (IOException e) {
        throw new POIXMLException("Failed to clone sheet data", e);
    }
}

/**
 * Unsets sheet features that cloning does not support
 * 
 * @param ct
 */
private void unsetUnsupportedFeatures(CTWorksheet ct) {

    // Comments feature
    if (ct.isSetLegacyDrawing()) {
        logger.log(POILogger.WARN, "Cloning sheets with comments is not yet supported.");
        ct.unsetLegacyDrawing();
    }

    // Page setup feature
    if (ct.isSetPageSetup()) {
        logger.log(POILogger.WARN, "Cloning sheets with page setup is not yet supported.");
        ct.unsetPageSetup();
    }
}

/**
 * Clones the sheet drawing with its relations
 * 
 * @param ct
 * @param drawing
 * @param srcSheet
 * @param clonedSheet
 */
private void cloneDrawing(CTWorksheet ct, XSSFDrawing drawing, XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    if (ct.isSetDrawing()) {
        // unset the existing reference to the drawing, so that
        // subsequent call of clonedSheet.createDrawingPatriarch() will create a new one
        ct.unsetDrawing();
    }
    XSSFDrawing clonedDrawing = clonedSheet.createDrawingPatriarch();

    // copy drawing contents
    clonedDrawing.getCTDrawing().set(drawing.getCTDrawing());

    clonedDrawing = clonedSheet.createDrawingPatriarch();

    cloneDrawingRelations(clonedDrawing, srcSheet);
}

/**
 * Clones relations from the old sheet to the new sheet drawing
 * 
 * @param clonedDrawing
 * @param srcSheet
 */
private void cloneDrawingRelations(XSSFDrawing clonedDrawing, XSSFSheet srcSheet) {
    List<RelationPart> srcRels = srcSheet.createDrawingPatriarch().getRelationParts();
    for (RelationPart rp : srcRels) {
        addRelation(rp, clonedDrawing);
    }
}