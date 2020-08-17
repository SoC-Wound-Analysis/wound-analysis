package sg.edu.woundanalysis

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

/**
 * Writes the values of the depth array to the cells of a worksheet.
 */
fun writeToExcelFile(outputDirectory: File, depthArray : Array<Int>) {
    //Instantiate Excel workbook:
    val xlWb = XSSFWorkbook()
    //Instantiate Excel worksheet:
    val xlWs = xlWb.createSheet()

    //Write text value to cell located at ROW_NUMBER / COLUMN_NUMBER:
    for (rowNumber in 0 until TOF_HEIGHT) {
        val row = xlWs.createRow(rowNumber)
        for (columnNumber in 0 until TOF_WIDTH) {
            row.createCell(columnNumber)
                    .setCellValue(depthArray[rowNumber * TOF_WIDTH + columnNumber].toString())
        }
    }

    //Write file:
    val excelFile = File(outputDirectory, "BITMAP_${MainActivity.SDF.format(Date())}.xlsx")
    val outputStream = FileOutputStream(excelFile)
    xlWb.write(outputStream)
    outputStream.close()
}

/**
 * Reads the value from the cell at the first row and first column of worksheet.
 */
fun readFromExcelFile(filepath: String) {
    val inputStream = FileInputStream(filepath)
    //Instantiate Excel workbook using existing file:
    var xlWb = WorkbookFactory.create(inputStream)

    //Row index specifies the row in the worksheet (starting at 0):
    val rowNumber = 0
    //Cell index specifies the column within the chosen row (starting at 0):
    val columnNumber = 0

    //Get reference to first sheet:
    val xlWs = xlWb.getSheetAt(0)
    println(xlWs.getRow(rowNumber).getCell(columnNumber))
}