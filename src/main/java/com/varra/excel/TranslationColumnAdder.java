/*
 * mfu - TranslationColumnAdder.java, Dec 03, 2019 20:26:16 PM
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.varra.excel;

import com.varra.util.RegexUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Spliterator;
import java.util.function.BiFunction;

import static com.varra.util.RegexUtils.parseNgetFirst;
import static java.util.Objects.nonNull;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.io.FilenameUtils.removeExtension;

/**
 * TODO Description go here.
 *
 * @author Rajakrishna V. Reddy
 * @version 1.0
 */
public class TranslationColumnAdder
{
    private static final BiFunction<? super Cell, String, Boolean> notEmptyCell = (Cell c, String lang) -> c != null && c.getStringCellValue() != null && !c.getStringCellValue().isEmpty() && c.getStringCellValue().equalsIgnoreCase(lang);
    private static final String STANDARD_LANG_CODE = "en";

    private static void addLanguageColumn(String filenameWithoutExtension, Workbook workbook, String language)
    {
        stream(spliteratorUnknownSize(workbook.sheetIterator(), Spliterator.ORDERED), false)
                .filter(s -> hasEngColumn(s.getRow(0)))
                .map(s -> addColumnToSheet(filenameWithoutExtension, s, language))
                .map(Sheet::getSheetName)
                .forEach(System.out::println);
    }

    private static Sheet addColumnToSheet(String filename, Sheet sheet, String language)
    {
        final int standardCellIndex = indexOfLangColumn(sheet.getRow(0), STANDARD_LANG_CODE);
        final BiFunction<String, String, String> getCellValue = (row, col) -> filename+"_xlsx_"+sheet.getSheetName()+"_Sheet_"+row+"_Row_"+col+"_Col";
        if (standardCellIndex >= 0)
        {
            int desiredCellNumber = indexOfLangColumn(sheet.getRow(0), language);
            addHeaderRow(desiredCellNumber, standardCellIndex, language, sheet.getRow(0));
            desiredCellNumber = indexOfLangColumn(sheet.getRow(0), language);
            final int lastRowNumber = sheet.getLastRowNum();
            for (int index = 1; index <= lastRowNumber; index++)
            {
                final Row row = sheet.getRow(index);
                final Cell cell = desiredCellNumber < row.getLastCellNum() ? row.getCell(desiredCellNumber) : row.createCell(desiredCellNumber);
                final CellStyle cellStyle = desiredCellNumber < row.getLastCellNum() ? cell.getCellStyle() : row.getCell(standardCellIndex).getCellStyle();
                cell.setCellStyle(cellStyle);
                final String rowAddress = parseNgetFirst(cell.getAddress().formatAsString(), "\\D+(\\d+)");
                final String colAddress = parseNgetFirst(cell.getAddress().formatAsString(), "(\\D+)\\d+");
                cell.setCellValue(getCellValue.apply(rowAddress, colAddress));
            }
        }
        return sheet;
    }

    private static void addHeaderRow(int desiredCellNumber, int standardCellIndex, String language, Row row)
    {
        if (desiredCellNumber == -1)
        {
            final Cell standardCell = row.getCell(standardCellIndex);
            if (nonNull(standardCell))
            {
                final Cell cell = row.createCell(row.getLastCellNum());
                cell.setCellStyle(standardCell.getCellStyle());
                cell.setCellValue(language);
            }
        }
    }

    private static boolean hasEngColumn(Row row)
    {
        return stream(spliteratorUnknownSize(row.cellIterator(), Spliterator.ORDERED), false).anyMatch(c -> notEmptyCell.apply(c, STANDARD_LANG_CODE));
    }

    private static int indexOfLangColumn(Row row, String lang)
    {
        return stream(spliteratorUnknownSize(row.cellIterator(), Spliterator.ORDERED), false).filter(c -> notEmptyCell.apply(c, lang)).findAny().map(Cell::getAddress).map(CellAddress::getColumn).orElse(-1);
    }

    public static void main(String[] args) throws IOException
    {
        final Path sourcePath = Paths.get("D:\\varra\\code\\tdot\\projects\\da-gs-parser\\etc\\etf-files\\Content.xlsx");
        final InputStream fis = Files.newInputStream(sourcePath);
        final XSSFWorkbook workbook = new XSSFWorkbook(fis);
        addLanguageColumn(removeExtension(sourcePath.toFile().getName()), workbook, "engRK");
        fis.close();

        final OutputStream fos = Files.newOutputStream(Paths.get("D:\\varra\\code\\tdot\\projects\\da-gs-parser\\etc\\etf-files\\Content1.xlsx"));
        workbook.write(fos);
        workbook.close();
        fos.close();
    }
}
