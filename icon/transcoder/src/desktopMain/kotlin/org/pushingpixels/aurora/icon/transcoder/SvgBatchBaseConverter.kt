/*
 * Copyright (c) 2020-2021 Aurora, Kirill Grouchnikov. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  o Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  o Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  o Neither the name of the copyright holder nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.pushingpixels.aurora.icon.transcoder

import java.io.File
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

abstract class SvgBatchBaseConverter {
    internal fun getInputArgument(args: Array<String>, argumentName: String?, defaultValue: String?): String? {
        for (arg in args) {
            val split = arg.split("=").toTypedArray()
            if (split.size != 2) {
                println("Argument '$arg' unsupported")
                println(CHECK_DOCUMENTATION)
                exitProcess(1)
            }
            if (split[0].compareTo(argumentName!!) == 0) {
                return split[1]
            }
        }
        return defaultValue
    }

    internal fun transcodeAllFilesInFolder(
        inputFolder: File, outputFolder: File,
        outputClassNamePrefix: String, outputFileNameExtension: String,
        outputPackageName: String,
        templateFile: String
    ) {

        var totalCount = 0
        var successCount = 0

        inputFolder
            .walk(direction = FileWalkDirection.TOP_DOWN)
            .maxDepth(1)
            .filter { it.isFile && it.name.endsWith("svg") }
            .forEach { file ->
                val svgClassName = (outputClassNamePrefix + file.name.substring(0, file.name.length - 4))
                    .replace('-', '_')
                    .replace(' ', '_')
                val classFilename = outputFolder.absolutePath + File.separator +
                        svgClassName + outputFileNameExtension
                println("Processing ${file.absolutePath} to $classFilename")
                totalCount++
                try {
                    PrintWriter(classFilename).use { writer ->
                        SvgBatchBaseConverter::class.java.getResourceAsStream(templateFile).use { templateStream ->
                            Objects.requireNonNull(templateStream, "Couldn't load $templateFile")
                            val latch = CountDownLatch(1)
                            val uri = file.toURI().toURL().toString()
                            val transcoder = SvgTranscoder(uri, svgClassName)
                            transcoder.setPackageName(outputPackageName)
                            transcoder.setListener(object : TranscoderListener {
                                override val writer = writer

                                override fun finished() {
                                    latch.countDown()
                                }
                            })
                            transcoder.transcode(templateStream)
                            // Limit the processing to 10 seconds to prevent infinite hang
                            latch.await(10, TimeUnit.SECONDS)
                            successCount++
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace(System.err)
                }

            }
        println()
        println("Processed $successCount out of $totalCount SVG files")
        println()
    }

    companion object {
        const val CHECK_DOCUMENTATION = "Check the documentation for the parameters to pass"
    }
}
