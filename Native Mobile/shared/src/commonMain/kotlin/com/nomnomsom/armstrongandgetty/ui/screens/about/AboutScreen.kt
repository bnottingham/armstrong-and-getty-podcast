package com.nomnomsom.armstrongandgetty.ui.screens.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsEvents
import com.nomnomsom.armstrongandgetty.analytics.AnalyticsTracker
import com.nomnomsom.armstrongandgetty.ui.icons.AppIcons
import com.nomnomsom.armstrongandgetty.ui.icons.Article
import com.nomnomsom.armstrongandgetty.ui.icons.Facebook
import com.nomnomsom.armstrongandgetty.ui.icons.Headset
import com.nomnomsom.armstrongandgetty.ui.icons.Instagram
import com.nomnomsom.armstrongandgetty.ui.icons.Mail
import com.nomnomsom.armstrongandgetty.ui.icons.OpenInNew
import com.nomnomsom.armstrongandgetty.ui.icons.Phone
import com.nomnomsom.armstrongandgetty.ui.icons.Public
import com.nomnomsom.armstrongandgetty.ui.icons.ShoppingBag
import com.nomnomsom.armstrongandgetty.ui.icons.XLogo
import com.nomnomsom.armstrongandgetty.ui.icons.YouTube
import com.nomnomsom.armstrongandgetty.ui.theme.CardBg
import com.nomnomsom.armstrongandgetty.ui.theme.DarkBg
import com.nomnomsom.armstrongandgetty.ui.theme.Gold
import com.nomnomsom.armstrongandgetty.ui.theme.SurfaceVariant
import com.nomnomsom.armstrongandgetty.ui.theme.TextMuted
import com.nomnomsom.armstrongandgetty.ui.theme.TextSecondary
import org.koin.compose.koinInject

private const val SITE = "https://www.armstrongandgetty.com"
private const val DEVELOPER_EMAIL_URI =
    "mailto:brett@nomnomsom.com?subject=Armstrong%20%26%20Getty%20App"

@Composable
fun AboutScreen() {
    val uriHandler = LocalUriHandler.current
    val analytics: AnalyticsTracker = koinInject()

    fun open(linkKey: String, url: String) {
        analytics.logEvent(
            AnalyticsEvents.ABOUT_LINK_OPEN,
            mapOf(AnalyticsEvents.PARAM_LINK to linkKey)
        )
        runCatching { uriHandler.openUri(url) }
            .onFailure { analytics.recordError("about link failed: $linkKey", it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        HeroCard()

        SectionHeader("THE SHOW")
        LinkRow(AppIcons.Public, "Official Website", "armstrongandgetty.com") {
            open("website", "$SITE/")
        }
        LinkRow(AppIcons.Headset, "How to Listen", "Stations, streams & apps") {
            open("how_to_listen", "$SITE/howtolisten/")
        }
        LinkRow(AppIcons.Article, "Hot Links", "Stories from today's show") {
            open("hot_links", "$SITE/featured/armstrong-gettys-hot-links/")
        }

        SectionHeader("SHOP")
        ShopCard { open("store", "https://armstrongandgetty.store/") }

        SectionHeader("GET IN TOUCH")
        LinkRow(AppIcons.Phone, "Call the Show", "415-295-KFTC (5382)") {
            open("call_show", "tel:+14152955382")
        }
        LinkRow(AppIcons.Mail, "Contact the Show", "armstrongandgetty.com/contact") {
            open("contact_show", "$SITE/contact/")
        }

        SectionHeader("FOLLOW A&G")
        SocialRow(::open)

        SectionHeader("MORE FROM A&G")
        LinkRow(AppIcons.GraphicEq, "One More Thing", "A little more A&G after the show") {
            open(
                "podcast_one_more_thing",
                "https://www.iheart.com/podcast/64-armstrong-getty-one-more-th-30416018/"
            )
        }
        LinkRow(AppIcons.GraphicEq, "Extra Large Interviews", "Full-length conversations") {
            open(
                "podcast_extra_large",
                "https://www.iheart.com/podcast/64-armstrong-getty-extra-large-30400323/"
            )
        }
        LinkRow(AppIcons.GraphicEq, "Select Cuts", "Hand-picked highlights") {
            open(
                "podcast_select_cuts",
                "https://www.iheart.com/podcast/1248-armstrong-getty-select-cu-62450145/"
            )
        }

        SectionHeader("APP DEVELOPER")
        DeveloperCard { open("developer_email", DEVELOPER_EMAIL_URI) }

        Text(
            "Unofficial companion app. Not affiliated with Armstrong & Getty, " +
                "iHeartMedia, or their affiliates.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 32.dp)
        )
    }
}

@Composable
private fun HeroCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                "A&G",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Gold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ARMSTRONG & GETTY",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "SMART. FUNNY. INDEPENDENT PERSPECTIVE.",
                style = MaterialTheme.typography.labelSmall,
                color = Gold
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Jack Armstrong and Joe Getty take an independent, contrarian look at " +
                    "news, politics, and modern life — merciless critics of mainstream " +
                    "media, on the air across the country every weekday morning.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Gold.copy(alpha = 0.12f))
            ) {
                Icon(icon, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                AppIcons.OpenInNew,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ShopCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gold)
            ) {
                Icon(
                    AppIcons.ShoppingBag,
                    contentDescription = null,
                    tint = DarkBg,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("A&G Store", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Official Armstrong & Getty merch",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                AppIcons.OpenInNew,
                contentDescription = null,
                tint = Gold,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SocialRow(open: (String, String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SocialButton(AppIcons.Facebook, "Facebook") {
            open("social_facebook", "https://www.facebook.com/aandgshow")
        }
        SocialButton(AppIcons.XLogo, "X") {
            open("social_x", "https://x.com/AandGShow")
        }
        SocialButton(AppIcons.YouTube, "YouTube") {
            open("social_youtube", "https://www.youtube.com/ArmstrongGetty")
        }
        SocialButton(AppIcons.Instagram, "Instagram") {
            open("social_instagram", "https://www.instagram.com/armstrongandgetty")
        }
    }
}

@Composable
private fun SocialButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = Gold, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DeveloperCard(onEmailClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text("Contact the Developer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "This app is an independent, fan-built companion for the show. Found a " +
                    "bug, have a feature idea, or just want to say hi? I read every message.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onEmailClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = DarkBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    AppIcons.Mail,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                // labelLarge bakes in a gold color, invisible on the gold button
                Text("Email the Developer", color = DarkBg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
