package me.kavishdevar.librepods.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.launch
import me.kavishdevar.librepods.BuildConfig
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.updates.UpdateItem
import me.kavishdevar.librepods.presentation.components.AppearanceStyleToggle
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
            style = MaterialTheme.typography.displayMedium,
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
        HorizontalPager(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = state,
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                if (index < updates.size) {
                    FeaturePage(item = updates[index])
                } else {
                    FinishPage(
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
private fun FeaturePage(item: UpdateItem) {
    val cardShape = RoundedCornerShape(32.dp)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(item.screenshotRes),
                    contentDescription = stringResource(item.titleRes),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
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

@Composable
private fun FinishPage(onFinish: () -> Unit) {
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
