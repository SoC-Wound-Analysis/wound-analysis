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
fun writeToTxtFile(outputDirectory: File, depthArray : Array<Int>) {
    val txtFile = File(outputDirectory, "BITMAP_${MainActivity.SDF.format(Date())}.txt")
    val outputStream = FileOutputStream(txtFile)

    //Write text value to the .txt File, separated by a -:
    for (rowNumber in 0 until TOF_HEIGHT) {
        for (columnNumber in 0 until TOF_WIDTH) {
            outputStream.write(depthArray[rowNumber * TOF_WIDTH + columnNumber])
            if (rowNumber * TOF_WIDTH + columnNumber != TOF_HEIGHT * TOF_WIDTH - 1) {
                outputStream.write("-".toByteArray())
            }
        }
    }

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