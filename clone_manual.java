import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Create an XSSFSheet from an existing sheet in the XSSFWorkbook.
 *  The cloned sheet is a deep copy of the original but with a new given
 *  name.
 *
 * @param sheetNum The index of the sheet to clone
 * @param newName The name to set for the newly created sheet
 * @return XSSFSheet representing the cloned sheet.
 * @throws IllegalArgumentException if the sheet index or the sheet
 *         name is invalid
 * @throws POIXMLException if there were errors when cloning
 */
public XSSFSheet cloneSheet(int sheetNum, String newName) {
    validateSheetIndex(sheetNum);
    XSSFSheet srcSheet = sheets.get(sheetNum);

    String newValidName = getNewValidSheetName(newName);
    XSSFSheet clonedSheet = createSheet(newName);

    XSSFDrawing drawing = cloneSheetRelations(srcSheet, clonedSheet);
    cloneSheetData(srcSheet, clonedSheet);

    CTWorksheet ct = clonedSheet.getCTWorksheet();
    unsetUnsupportedFeatures(ct);

    clonedSheet.setSelected(false);

    // clone the sheet drawing along with its relationships
    if (drawing != null) {
        cloneDrawing(ct, drawing, srcSheet, clonedSheet);
    }
    return clonedSheet;
}

private String getNewValidSheetName(String newName) {
    if (newName == null) {
        String srcName = srcSheet.getSheetName();
        newName = getUniqueSheetName(srcName);
    } else {
        validateSheetName(newName);
    }

    return newName;
}

private XSSFDrawing cloneSheetRelations(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    // copy sheet's relations
    List<RelationPart> rels = srcSheet.getRelationParts();
    // if the sheet being cloned has a drawing then remember it and re-create it too
    XSSFDrawing drawing = null;
    for(RelationPart rp : rels) {
        POIXMLDocumentPart r = rp.getDocumentPart();
        // do not copy the drawing relationship, it will be re-created
        if(r instanceof XSSFDrawing) {
            drawing = (XSSFDrawing)r;
            continue;
        }

        addRelation(rp, clonedSheet);
    }

    copyExternalRelations(srcSheet, clonedSheet);
    return drawing;
}

private void copyExternalRelations(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try {
        for(PackageRelationship pr : srcSheet.getPackagePart().getRelationships()) {
            if (pr.getTargetMode() == TargetMode.EXTERNAL) {
                clonedSheet.getPackagePart().addExternalRelationship
                        (pr.getTargetURI().toASCIIString(), pr.getRelationshipType(), pr.getId());
            }
        }
    } catch (InvalidFormatException e) {
        throw new POIXMLException("Failed to clone sheet", e);
    }
}

private void cloneSheetData(XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    try (ByteArrayOutputStream src_out = new ByteArrayOutputStream()) {
        srcSheet.write(src_out);
        try (ByteArrayInputStream src_in = new ByteArrayInputStream(src_out.toByteArray())) {
            clonedSheet.read(src_in);
        }
    } catch (IOException e){
        throw new POIXMLException("Failed to clone sheet", e);
    }
}

private void unsetUnsupportedFeatures(CTWorksheet ct) {
    if(ct.isSetLegacyDrawing()) {
        logger.log(POILogger.WARN, "Cloning sheets with comments is not yet supported.");
        ct.unsetLegacyDrawing();
    }
    if (ct.isSetPageSetup()) {
        logger.log(POILogger.WARN, "Cloning sheets with page setup is not yet supported.");
        ct.unsetPageSetup();
    }
}

private void cloneDrawing(CTWorksheet ct, XSSFDrawing drawing, XSSFSheet srcSheet, XSSFSheet clonedSheet) {
    if(ct.isSetDrawing()) {
        // unset the existing reference to the drawing,
        // so that subsequent call of clonedSheet.createDrawingPatriarch() will create a new one
        ct.unsetDrawing();
    }
    XSSFDrawing clonedDrawing = clonedSheet.createDrawingPatriarch();
    // copy drawing contents
    clonedDrawing.getCTDrawing().set(drawing.getCTDrawing());

    clonedDrawing = clonedSheet.createDrawingPatriarch();
    
    cloneDrawingRelations(clonedDrawing, srcSheet);
}

private void cloneDrawingRelations(XSSFDrawing clonedDrawing, XSSFSheet srcSheet) {
    // Clone drawing relations
    List<RelationPart> srcRels = srcSheet.createDrawingPatriarch().getRelationParts();
    for (RelationPart rp : srcRels) {
        addRelation(rp, clonedDrawing);
    }
}