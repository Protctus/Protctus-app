package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LightGBM model loader and inference engine.
 *
 * Loads a LightGBM text model file (.txt) exported from the LightGBM training pipeline
 * and performs inference by traversing each decision tree directly. No ONNX runtime
 * dependency is required, making this suitable for deployment on any Android device.
 *
 * Model format (LightGBM text dump):
 *   Header: tree, version, num_class, num_tree_per_iteration, label_index,
 *           max_feature_idx, objective, feature_names, feature_infos,
 *           tree_sizes
 *   Per tree: Tree=N, num_leaves, num_cat, split_feature, split_gain, threshold,
 *             decision_type, left_child, right_child, leaf_value, leaf_weight,
 *             leaf_count, internal_value, internal_weight, internal_count,
 *             is_linear, shrinkage
 *
 * Thread-safe: all methods are pure after initialization.
 */
class LightGBMModel private constructor(
    private val trees: List<TreeModel>,
    private val shrinkage: Float,
    private val baseline: Float,
    private val featureCount: Int
) {

    private companion object {
        private const val TAG = "LightGBMModel"
    }

    /**
     * Load a LightGBM text model from assets.
     *
     * @param context Android context
     * @param assetName Asset file name (e.g., "lightgbm_model.txt")
     * @return Loaded model, or null if loading fails
     */
    @JvmStatic
    fun loadFromAssets(context: Context, assetName: String): LightGBMModel? {
        return try {
            context.assets.open(assetName).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                loadFromReader(reader)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LightGBM model from assets: $assetName", e)
            null
        }
    }

    private fun loadFromReader(reader: BufferedReader): LightGBMModel? {
        return try {
            // Parse header
            var line: String? = reader.readLine()
            var numTrees = 0
            var featureCount = 0
            var shrinkage = 1f
            var baseline = 0f

            while (line != null) {
                when {
                    line.startsWith("tree") -> {
                        // Skip tree header line
                    }
                    line.startsWith("version=") -> {}
                    line.startsWith("num_class=") -> {}
                    line.startsWith("num_tree_per_iteration=") -> {
                        numTrees = line.substringAfter("=").toInt()
                    }
                    line.startsWith("label_index=") -> {}
                    line.startsWith("max_feature_idx=") -> {
                        featureCount = line.substringAfter("=").toInt() + 1
                    }
                    line.startsWith("objective=") -> {}
                    line.startsWith("feature_names=") -> {}
                    line.startsWith("feature_infos=") -> {}
                    line.startsWith("tree_sizes=") -> {}
                    line.startsWith("shrinkage=") -> {
                        shrinkage = line.substringAfter("=").toFloat()
                    }
                    line == "Tree=0" -> {
                        // Start of first tree
                        break
                    }
                }
                line = reader.readLine()
            }

            // Parse all trees
            val trees = mutableListOf<TreeModel>()
            var currentTree: TreeModel? = null

            while (line != null) {
                if (line.startsWith("Tree=")) {
                    if (currentTree != null) {
                        trees.add(currentTree)
                    }
                    currentTree = parseTree(reader, line)
                }
                line = reader.readLine()
            }
            if (currentTree != null) {
                trees.add(currentTree)
            }

            LightGBMModel(trees, shrinkage, baseline, featureCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LightGBM model", e)
            null
        }
    }

    private fun parseTree(reader: BufferedReader, treeHeader: String): TreeModel {
        val treeIndex = treeHeader.substringAfter("Tree=").toInt()
        var numLeaves = 0
        var splitFeatures = mutableListOf<Int>()
        var thresholds = mutableListOf<Float>()
        var decisionTypes = mutableListOf<Int>()
        var leftChildren = mutableListOf<Int>()
        var rightChildren = mutableListOf<Int>()
        var leafValues = mutableListOf<Float>()
        var leafWeights = mutableListOf<Float>()
        var leafCounts = mutableListOf<Int>()

        var line: String? = reader.readLine()
        while (line != null && !line.startsWith("Tree=")) {
            when {
                line.startsWith("num_leaves=") -> {
                    numLeaves = line.substringAfter("=").toInt()
                }
                line.startsWith("num_cat=") -> {}
                line.startsWith("split_feature=") -> {
                    splitFeatures = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                }
                line.startsWith("split_gain=") -> {}
                line.startsWith("threshold=") -> {
                    thresholds = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toFloat() }
                }
                line.startsWith("decision_type=") -> {
                    decisionTypes = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                }
                line.startsWith("left_child=") -> {
                    leftChildren = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                }
                line.startsWith("right_child=") -> {
                    rightChildren = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                }
                line.startsWith("leaf_value=") -> {
                    leafValues = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toFloat() }
                }
                line.startsWith("leaf_weight=") -> {
                    leafWeights = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toFloat() }
                }
                line.startsWith("leaf_count=") -> {
                    leafCounts = line.substringAfter("=")
                        .split(" ")
                        .filter { it.isNotEmpty() }
                        .map { it.toInt() }
                }
                line.startsWith("internal_value=") -> {}
                line.startsWith("internal_weight=") -> {}
                line.startsWith("internal_count=") -> {}
                line.startsWith("is_linear=") -> {}
                line.startsWith("shrinkage=") -> {}
            }
            line = reader.readLine()
        }

        return TreeModel(
            treeIndex = treeIndex,
            numLeaves = numLeaves,
            splitFeatures = splitFeatures,
            thresholds = thresholds,
            decisionTypes = decisionTypes,
            leftChildren = leftChildren,
            rightChildren = rightChildren,
            leafValues = leafValues,
            leafWeights = leafWeights,
            leafCounts = leafCounts
        )
    }

    /**
     * Run inference on a feature vector.
     *
     * @param features FloatArray of length featureCount
     * @return Phishing probability in [0.0, 1.0]
     */
    fun infer(features: FloatArray): Float {
        if (features.size != featureCount) {
            Log.w(TAG, "Feature count mismatch: expected $featureCount, got ${features.size}")
            return 0.0f
        }

        var rawScore = baseline
        for (tree in trees) {
            val leafIndex = traverseTree(tree, features)
            rawScore += tree.leafValues[leafIndex] * shrinkage
        }

        // Apply sigmoid to get probability
        return 1.0f / (1.0f + kotlin.math.exp(-rawScore))
    }

    private fun traverseTree(tree: TreeModel, features: FloatArray): Int {
        var nodeId = 0
        while (true) {
            val decisionType = tree.decisionTypes[nodeId]
            val featureIdx = tree.splitFeatures[nodeId]
            val threshold = tree.thresholds[nodeId]

            if (decisionType == 2) {
                // Numerical split
                val featureValue = if (featureIdx < features.size) features[featureIdx] else 0.0f
                if (featureValue <= threshold) {
                    val left = tree.leftChildren[nodeId]
                    if (left < 0) {
                        // Leaf node: negative value encodes leaf index
                        return -left - 1
                    }
                    nodeId = left
                } else {
                    val right = tree.rightChildren[nodeId]
                    if (right < 0) {
                        return -right - 1
                    }
                    nodeId = right
                }
            } else {
                // Should not happen for LightGBM v4 text models
                Log.w(TAG, "Unexpected decision type: $decisionType")
                return 0
            }
        }
    }

    /**
     * Get the number of trees in the model.
     */
    val treeCount: Int
        get() = trees.size

    /**
     * Get the feature count expected by the model.
     */
    val expectedFeatureCount: Int
        get() = featureCount

    /**
     * Internal tree model representation.
     */
    data class TreeModel(
        val treeIndex: Int,
        val numLeaves: Int,
        val splitFeatures: List<Int>,
        val thresholds: List<Float>,
        val decisionTypes: List<Int>,
        val leftChildren: List<Int>,
        val rightChildren: List<Int>,
        val leafValues: List<Float>,
        val leafWeights: List<Float>,
        val leafCounts: List<Int>
    )
}
