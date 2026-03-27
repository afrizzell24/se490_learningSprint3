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


    // copy sheet's relations
    List<RelationPart> rels = srcSheet.getRelationParts();
    // if the sheet being cloned has a drawing then remember it and re-create it too
    XSSFDrawing dg = null;
    for(RelationPart rp : rels) {
        POIXMLDocumentPart r = rp.getDocumentPart();
        // do not copy the drawing relationship, it will be re-created
        if(r instanceof XSSFDrawing) {
            dg = (XSSFDrawing)r;
            continue;
        }

        addRelation(rp, clonedSheet);
    }

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


    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        srcSheet.write(out);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(out.toByteArray())) {
            clonedSheet.read(bis);
        }
    } catch (IOException e){
        throw new POIXMLException("Failed to clone sheet", e);
    }
    CTWorksheet ct = clonedSheet.getCTWorksheet();
    if(ct.isSetLegacyDrawing()) {
        logger.log(POILogger.WARN, "Cloning sheets with comments is not yet supported.");
        ct.unsetLegacyDrawing();
    }
    if (ct.isSetPageSetup()) {
        logger.log(POILogger.WARN, "Cloning sheets with page setup is not yet supported.");
        ct.unsetPageSetup();
    }

    clonedSheet.setSelected(false);

    // clone the sheet drawing along with its relationships
    if (dg != null) {
        if(ct.isSetDrawing()) {
            // unset the existing reference to the drawing,
            // so that subsequent call of clonedSheet.createDrawingPatriarch() will create a new one
            ct.unsetDrawing();
        }
        XSSFDrawing clonedDg = clonedSheet.createDrawingPatriarch();
        // copy drawing contents
        clonedDg.getCTDrawing().set(dg.getCTDrawing());

        clonedDg = clonedSheet.createDrawingPatriarch();

        // Clone drawing relations
        List<RelationPart> srcRels = srcSheet.createDrawingPatriarch().getRelationParts();
        for (RelationPart rp : srcRels) {
            addRelation(rp, clonedDg);
        }
    }
    return clonedSheet;
}

private XSSFSheet makeSheet(int sheetNum, String newName) {
    validateSheetIndex(sheetNum);
    XSSFSheet srcSheet = sheets.get(sheetNum);

    if (newName == null) {
        String srcName = srcSheet.getSheetName();
        newName = getUniqueSheetName(srcName);
    } else {
        validateSheetName(newName);
    }

    XSSFSheet clonedSheet = createSheet(newName);
}