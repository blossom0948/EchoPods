package me.kavishdevar.librepods.presentation.screens

import androidx.activity.compose.BackHandler
import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.updates.UpdateItem
import me.kavishdevar.librepods.presentation.components.AppearanceStyleToggle
import me.kavishdevar.librepods.presentation.components.liquidGlassSliderThumb
import me.kavishdevar.librepods.presentation.components.rememberMomentumAnimation
import me.kavishdevar.librepods.presentation.theme.DesignSystem
import me.kavishdevar.librepods.presentation.theme.LocalDesignSystem
import kotlin.math.min

/**
 * Release guide based on LibrePods' original release-notes carousel. Feature
 * artwork and its explanation have distinct, contained areas so the image is
 * not cropped or crowded by decorative shapes on compact screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesScreen(
    updates: List<UpdateItem>,
    isFirstRun: Boolean = false,
    releaseNotesShown: () -> Unit,
) {
    if (updates.isEmpty()) {
        releaseNotesShown()
        return
    }

    val versionName = BuildConfig.VERSION_NAME.removeSuffix("-debug").removeSuffix("-play")
    val pageCount = updates.size + 1
    val state = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val appleStyle = LocalDesignSystem.current == DesignSystem.Apple
    val guideTitleStyle = if (appleStyle) {
        MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.35).sp,
        )
    } else {
        MaterialTheme.typography.displayMedium
    }

    BackHandler(enabled = state.currentPage > 0) {
        scope.launch { state.animateScrollToPage(state.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(topPadding))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppearanceStyleToggle()
        }
        Text(
            text = if (isFirstRun) {
                stringResource(R.string.guide_title)
            } else {
                stringResource(R.string.whats_new_version, versionName)
            },
            style = guideTitleStyle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        val versionLabel = "${stringResource(R.string.version)} $versionName"
        val versionUrl = "https://github.com/blossom0948/EchoPods/releases/tag/v$versionName"
        val versionColor = MaterialTheme.colorScheme.primary
        val versionLink = buildAnnotatedString {
            append(versionLabel)
            addLink(
                url = LinkAnnotation.Url(
                    url = versionUrl,
                    styles = TextLinkStyles(style = SpanStyle(color = versionColor)),
                ),
                start = 0,
                end = versionLabel.length,
            )
        }
        Text(
            text = versionLink,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
            textDecoration = TextDecoration.Underline,
        )

        Spacer(Modifier.height(8.dp))
        val guideBackdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                    backdrop = rememberLayerBackdrop(),
                    exportedBackdrop = guideBackdrop,
                    shape = { RectangleShape },
                    highlight = { Highlight.Ambient.copy(alpha = 0f) },
                    effects = {},
                    ),
            )
            HorizontalPager(
                modifier = Modifier.fillMaxSize(),
                state = state,
            ) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    if (index < updates.size) {
                        FeaturePage(item = updates[index], isActive = index == state.currentPage)
                    }
                }
            }

            // Keep backdrop sampling out of HorizontalPager's precomposed page
            // tree. Fold4/Android 16 otherwise recurses through RenderNodes.
            if (state.currentPage == updates.size) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    FinishPage(
                        backdrop = guideBackdrop,
                        onFinish = releaseNotesShown,
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { page ->
                Box(
                    Modifier
                        .size(if (page == state.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == state.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun FeaturePage(item: UpdateItem, isActive: Boolean) {
    val cardShape = RoundedCornerShape(32.dp)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (item.detailScreenshotRes != null) {
            FeaturePhoneReveal(item = item, isActive = isActive)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.15f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(item.screenshotRes),
                        contentDescription = stringResource(item.titleRes),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(28.dp))
                            .padding(8.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(item.descriptionRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Starts with the full phone screen, then zooms into a legible feature crop. */
@Composable
private fun FeaturePhoneReveal(item: UpdateItem, isActive: Boolean) {
    val progress = remember { Animatable(0f) }
    val context = LocalContext.current
    val animationsEnabled = remember(context) { ValueAnimator.areAnimatorsEnabled() }
    val designSystem = LocalDesignSystem.current
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val deviceFrameBrush = if (designSystem == DesignSystem.Apple) {
        Brush.linearGradient(
            listOf(
                Color(0xFFF0F5FC),
                Color(0xFF7A899F),
                Color(0xFFDCE8F6),
                Color(0xFF69788D),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                outline.copy(alpha = 0.72f),
                MaterialTheme.colorScheme.surfaceVariant,
                outline.copy(alpha = 0.72f),
            ),
        )
    }
    val archShape = MaterialShapes.Arch.toShape()
    val detailScreenshotRes = item.detailScreenshotRes ?: return

    LaunchedEffect(isActive, item.screenshotRes) {
        if (!isActive) {
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            delay(260)
            if (animationsEnabled) {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(760, easing = FastOutSlowInEasing),
                )
            } else {
                progress.snapTo(1f)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the handset fully inside its rounded page while the camera settles.
        val deviceScale = if (animationsEnabled) 0.82f + 0.18f * progress.value else 0.82f
        val handsetHeightFraction = if (designSystem == DesignSystem.Apple) 0.56f else 0.82f
        val handsetModifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .fillMaxHeight(handsetHeightFraction)
                .aspectRatio(0.49f)
                .graphicsLayer {
                    scaleX = deviceScale
                    scaleY = deviceScale
                    transformOrigin = TransformOrigin(0.5f, 0.42f)
                    translationY = -progress.value * 4.dp.toPx()
                }
        if (designSystem == DesignSystem.Apple) {
            // User-provided Bezel export: keep the screenshot inside its transparent display
            // opening, then draw the silver frame and camera detail above it.
            val handsetHeight = maxHeight * handsetHeightFraction
            val handsetWidth = handsetHeight * 0.49f
            val displayShape = RoundedCornerShape(handsetWidth * 0.15f)
            Box(modifier = handsetModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = handsetWidth * 0.035f,
                            vertical = handsetHeight * 0.015f,
                        )
                        .clip(displayShape),
                ) {
                    Image(
                        painter = painterResource(item.screenshotRes),
                        contentDescription = stringResource(item.titleRes),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.22f * progress.value)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val visibleAlpha = if (animationsEnabled) progress.value else 0f
                                if (visibleAlpha > 0f) {
                                    drawRoundRect(
                                        color = primary.copy(alpha = 0.76f * visibleAlpha),
                                        topLeft = Offset(size.width * 0.055f, size.height * 0.58f),
                                        size = Size(size.width * 0.89f, size.height * 0.38f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                            },
                    )
                }
                Image(
                    painter = painterResource(R.drawable.bezel_iphone_18_pro_glacier),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = handsetModifier
                    .clip(RoundedCornerShape(38.dp))
                    .background(deviceFrameBrush)
                    .border(1.dp, outline.copy(alpha = 0.82f), RoundedCornerShape(38.dp))
                    .padding(3.dp)
                    .clip(RoundedCornerShape(35.dp))
                    .background(Color(0xFF080A0E))
                    .padding(3.dp)
                    .clip(RoundedCornerShape(31.dp)),
            ) {
                Image(
                    painter = painterResource(item.screenshotRes),
                    contentDescription = stringResource(item.titleRes),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f * progress.value)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val visibleAlpha = if (animationsEnabled) progress.value else 0f
                            if (visibleAlpha > 0f) {
                                drawRoundRect(
                                    color = primary.copy(alpha = 0.76f * visibleAlpha),
                                    topLeft = Offset(size.width * 0.055f, size.height * 0.58f),
                                    size = Size(size.width * 0.89f, size.height * 0.38f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        },
                )
            }
        }

        val detailShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1.04f)
                .align(Alignment.Center)
                .offset(y = -maxHeight * 0.1f)
                .graphicsLayer {
                    alpha = progress.value
                    val revealScale = 0.38f + progress.value * 0.62f
                    scaleX = revealScale
                    scaleY = revealScale
                    translationY = (1f - progress.value) * 26.dp.toPx()
                }
                .shadow(14.dp, detailShape)
                .clip(detailShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, primary.copy(alpha = 0.42f), detailShape),
        ) {
            Image(
                painter = painterResource(detailScreenshotRes),
                contentDescription = stringResource(item.titleRes),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(detailShape),
            )
        }

        val panelColor = if (designSystem == DesignSystem.Apple) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val panelTextColor = if (designSystem == DesignSystem.Apple) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.36f)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(archShape)
                .background(panelColor)
                .border(1.dp, outline.copy(alpha = 0.56f), archShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.guide_new_feature_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = panelTextColor,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(item.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (designSystem == DesignSystem.Apple) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        panelTextColor.copy(alpha = 0.84f)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FinishPage(backdrop: Backdrop, onFinish: () -> Unit) {
    when (LocalDesignSystem.current) {
        DesignSystem.Apple -> IosFinishPage(backdrop = backdrop, onFinish = onFinish)
        DesignSystem.Material -> MaterialFinishPage(onFinish = onFinish)
    }
}

@Composable
private fun MaterialFinishPage(onFinish: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val morph = remember {
        Morph(MaterialShapes.Cookie7Sided.normalized(), MaterialShapes.SoftBurst.normalized())
    }
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "guideFinishMorph",
    )
    val rotation by animateFloatAsState(
        targetValue = if (pressed) -8f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "guideFinishPressRotation",
    )

    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .pointerInput(onFinish) {
                detectTapGestures(
                    onTap = { onFinish() },
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .drawBehind {
                    val shapePath = morph.toPath(progress = morphProgress, path = path)
                    val bounds = shapePath.getBounds()
                    val scale = min(size.width / bounds.width, size.height / bounds.height) * 0.9f
                    matrix.reset()
                    matrix.scale(scale, scale)
                    shapePath.transform(matrix)
                    shapePath.translate(size.center - shapePath.getBounds().center)
                    rotate(rotation) { drawPath(shapePath, color = tertiary) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.guide_start),
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

@Composable
private fun IosFinishPage(backdrop: Backdrop, onFinish: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val glassSurface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = 0.22f),
                                glassSurface.copy(alpha = 0.74f),
                                primary.copy(alpha = 0.14f),
                            ),
                        ),
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.52f), primary.copy(alpha = 0.20f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = primary,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.guide_finish_heading),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.25).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.guide_finish_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            LiquidGlassSlideToContinue(
                onFinish = onFinish,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun LiquidGlassSlideToContinue(
    onFinish: () -> Unit,
    backdrop: Backdrop,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val animationsEnabled = remember(context) { ValueAnimator.areAnimatorsEnabled() }
    val isDarkTheme = isSystemInDarkTheme()
    val slideFraction = androidx.compose.runtime.remember { mutableFloatStateOf(0f) }
    val isDragging = remember { mutableStateOf(false) }
    val completionStarted = remember { mutableStateOf(false) }
    val completionFired = remember { mutableStateOf(false) }
    val trackWidthPx = remember { mutableFloatStateOf(0f) }
    val thumbSize = 56.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val horizontalInsetPx = with(density) { 5.dp.toPx() }
    val maxOffsetPx = (trackWidthPx.floatValue - thumbSizePx - horizontalInsetPx * 2)
        .coerceAtLeast(0f)
    val settledFraction by animateFloatAsState(
        targetValue = if (completionStarted.value) 1f else slideFraction.floatValue,
        animationSpec = if (animationsEnabled) {
            spring(dampingRatio = 0.84f, stiffness = 380f)
        } else {
            snap()
        },
        label = "iosGuideSlideProgress",
    )
    // The thumb tracks the finger directly. The shared EchoPods momentum
    // animation supplies the elastic stretch and refraction response.
    val shownFraction = if (isDragging.value) slideFraction.floatValue else settledFraction
    val sliderBackdrop = rememberLayerBackdrop()
    val momentum = rememberMomentumAnimation(maxScale = 1.5f)
    val slideLabel = stringResource(R.string.guide_slide_to_continue)
    val accessibleActionLabel = stringResource(R.string.guide_start)

    LaunchedEffect(completionStarted.value) {
        if (completionStarted.value && !completionFired.value) {
            completionFired.value = true
            if (animationsEnabled) delay(180L)
            onFinish()
        }
    }

    val trackShape = RoundedCornerShape(36.dp)
    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth(0.94f)
            .height(68.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { trackShape },
                highlight = { Highlight.Ambient.copy(alpha = 0.24f) },
                shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.10f)) },
                innerShadow = { InnerShadow(radius = 2.dp, alpha = 0.12f) },
                effects = {
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 2.dp.toPx(),
                        refractionAmount = size.height * 0.10f,
                        depthEffect = true,
                        chromaticAberration = false,
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = if (isDarkTheme) 0.08f else 0.18f))
                },
            )
            .clip(trackShape)
            .onSizeChanged { trackWidthPx.floatValue = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                contentDescription = slideLabel
                role = Role.Button
                onClick(label = accessibleActionLabel) {
                    if (!completionStarted.value) completionStarted.value = true
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(sliderBackdrop),
        ) {
            Text(
                text = slideLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = (1f - shownFraction * 1.35f).coerceIn(0f, 1f)
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 5.dp)
                .size(thumbSize)
                .graphicsLayer { translationX = maxOffsetPx * shownFraction }
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (!completionStarted.value && maxOffsetPx > 0f) {
                            val previous = slideFraction.floatValue
                            val next = (previous + delta / maxOffsetPx).coerceIn(0f, 1f)
                            slideFraction.floatValue = next
                            if (previous < 0.90f && next >= 0.90f) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = !completionStarted.value,
                    startDragImmediately = true,
                    onDragStarted = { isDragging.value = true },
                    onDragStopped = {
                        isDragging.value = false
                        if (slideFraction.floatValue >= 0.90f) {
                            completionStarted.value = true
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        } else {
                            slideFraction.floatValue = 0f
                        }
                    },
                )
                .then(momentum.modifier)
                .liquidGlassSliderThumb(
                    backdrop = rememberCombinedBackdrop(backdrop, sliderBackdrop),
                    momentumAnimation = momentum,
                    shape = CircleShape,
                    surfaceColor = Color.White.copy(alpha = if (isDarkTheme) 0.40f else 0.72f),
                    chromaticAberration = false,
                    idleProgress = 0.16f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = lerp(
                    Color.Black,
                    if (isDarkTheme) Color.White else Color.Black,
                    momentum.progress,
                ),
            )
        }
    }
}
