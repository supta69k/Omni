# Omni — AI Context Map

> **Stack:** android, express | none | jetpack-compose | typescript
> **Monorepo:** omni-otp-backend, omni-functions, omni-seed-tools

> 11 routes (11 inferred) | 0 models | 263 components | 14 lib files | 11 env vars | 3 middleware
> **Token savings:** this file is ~0 tokens. Without it, AI exploration would cost ~0 tokens. **Saves ~0 tokens per conversation.**
> **Last scanned:** 2026-09-26 06:28 — re-run after significant changes

---

# Routes

### android

- `ACTIVITY` `/MainActivity` `[inferred]`

### express

- `POST` `/otp/send` [auth] `[inferred]`
- `POST` `/otp/verify` [auth, db] `[inferred]`
- `GET` `/health` `[inferred]`
- `POST` `/ai/meal/analyze` [auth] `[inferred]`
- `POST` `/verification/approve` [auth] `[inferred]`
- `POST` `/verification/reject` [auth] `[inferred]`
- `POST` `/posts/:postId/like` params(postId) [auth] `[inferred]`
- `POST` `/posts/:postId/comments` params(postId) [auth] `[inferred]`
- `POST` `/conversations/:conversationId/messages` params(conversationId) [auth] `[inferred]`
- `POST` `/admin/grant` [auth] `[inferred]`

---

# Components

- **OmniApp** [client] — `app\src\main\java\com\example\omni\MainActivity.kt`
- **AuthLogo** [client] — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **AuthHeader** [client] — props: title, subtitle — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **AuthField** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **AuthMessageSlot** [client] — props: message, isError — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **AuthPrimaryButton** [client] — props: label, onClick — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **AuthFooter** [client] — props: prompt, action, onAction — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **DividerLine** [client] — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **SocialButton** [client] — props: icon, contentDescription, onClick — `app\src\main\java\com\example\omni\ui\auth\AuthCommon.kt`
- **SignInScreen** [client] — props: onSignUp — `app\src\main\java\com\example\omni\ui\auth\SignInScreen.kt`
- **SignInScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\SignInScreen.kt`
- **SignInScreenErrorPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\SignInScreen.kt`
- **SignUpScreen** [client] — props: onSignIn — `app\src\main\java\com\example\omni\ui\auth\SignUpScreen.kt`
- **TermsRow** [client] — `app\src\main\java\com\example\omni\ui\auth\SignUpScreen.kt`
- **SignUpScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\SignUpScreen.kt`
- **SignUpScreenErrorPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\SignUpScreen.kt`
- **VerifyEmailScreen** [client] — props: email, code — `app\src\main\java\com\example\omni\ui\auth\VerifyEmailScreen.kt`
- **VerifyEmailScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\VerifyEmailScreen.kt`
- **VerifyEmailScreenCodeErrorPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\VerifyEmailScreen.kt`
- **VerifyEmailScreenSuccessPreview** [client] — `app\src\main\java\com\example\omni\ui\auth\VerifyEmailScreen.kt`
- **ComingSoonScreen** [client] — props: title, detail, tab, onNavigate — `app\src\main\java\com\example\omni\ui\ComingSoonScreen.kt`
- **ComingSoonScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\ComingSoonScreen.kt`
- **AdaptiveRow** [client] — props: horizontalSpacing, columnIndex, columnCount — `app\src\main\java\com\example\omni\ui\components\AdaptiveGrid.kt`
- **OmniHeader** [client] — props: state — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **HeaderAction** [client] — props: icon, contentDescription, unread, onClick — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **OmniBottomNav** [client] — props: selected, onSelect — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **NavIconButton** [client] — props: item, selected, onClick — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **NavCell** [client] — props: item, selected, onClick — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **OmniTabScaffold** [client] — props: selected, onNavigate — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **OmniNavRail** [client] — props: selected, onSelect — `app\src\main\java\com\example\omni\ui\components\OmniChrome.kt`
- **OmniSheetScaffold** [client] — props: visible, onDismiss — `app\src\main\java\com\example\omni\ui\components\OmniSheetScaffold.kt`
- **DesignFrame** [client] — `app\src\main\java\com\example\omni\ui\DesignFrame.kt`
- **CommentsSheet** [client] — props: visible, comments, onDismiss — `app\src\main\java\com\example\omni\ui\feed\CommentsSheet.kt`
- **CommentsPanel** [client] — props: comments, onDismiss — `app\src\main\java\com\example\omni\ui\feed\CommentsSheet.kt`
- **CommentRowCard** [client] — props: comment, onOpenProfile — `app\src\main\java\com\example\omni\ui\feed\CommentsSheet.kt`
- **ComposePostScreen** [client] — props: onPost — `app\src\main\java\com\example\omni\ui\feed\ComposePostScreen.kt`
- **AttachPhotoTile** [client] — props: enabled, onClick — `app\src\main\java\com\example\omni\ui\feed\ComposePostScreen.kt`
- **PickedPhoto** [client] — props: image, enabled, onReplace — `app\src\main\java\com\example\omni\ui\feed\ComposePostScreen.kt`
- **ComposePostScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\feed\ComposePostScreen.kt`
- **CreateStorySheet** [client] — props: visible, image, isPublishing, errorText, onPickImage — `app\src\main\java\com\example\omni\ui\feed\CreateStorySheet.kt`
- **StoryPanel** [client] — props: image, isPublishing, errorText, onPickImage — `app\src\main\java\com\example\omni\ui\feed\CreateStorySheet.kt`
- **FeedScreen** [client] — props: header — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **FeedHeader** [client] — props: header — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **SearchResults** [client] — props: search, onOpenProfile — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **SearchNote** [client] — props: text — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **SearchResultRow** [client] — props: user, onClick — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **StoryStrip** [client] — props: tiles, myUid, myPhotoUrl, onOpenViewer — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **StoryTile** [client] — props: tile, onClick — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **ShareMealTile** [client] — props: hasStory, photoUrl, onOpenViewer — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **FeedSegments** [client] — props: selected, onSelect — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **SegmentTab** [client] — props: label, width, selected, onClick — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **FeedPost** [client] — props: post, avatarUrl, onLike — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **FollowPill** [client] — props: following, onClick — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **ActionPill** [client] — props: text, icon, contentDescription, emphasized, onClick — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **FeedScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\feed\FeedScreen.kt`
- **StoryViewer** [client] — props: authorName, stories, storyIndex, isMine, onAdvance — `app\src\main\java\com\example\omni\ui\feed\StoryViewer.kt`
- **StoryViewerPreview** [client] — `app\src\main\java\com\example\omni\ui\feed\StoryViewer.kt`
- **GuideDetailScreen** [client] — props: state, onToggleStep — `app\src\main\java\com\example\omni\ui\firstaid\GuideDetailScreen.kt`
- **StepCard** [client] — props: step, done, onToggle — `app\src\main\java\com\example\omni\ui\firstaid\GuideDetailScreen.kt`
- **TickBox** [client] — props: done — `app\src\main\java\com\example\omni\ui\firstaid\GuideDetailScreen.kt`
- **WarningCard** [client] — props: warnings — `app\src\main\java\com\example\omni\ui\firstaid\GuideDetailScreen.kt`
- **GuideDetailScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\firstaid\GuideDetailScreen.kt`
- **GuidesScreen** [client] — props: state — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **GuideSearchField** [client] — props: query, onQueryChange — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **GuideQueryField** [client] — props: query, onQueryChange — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **GuideCard** [client] — props: card, onClick — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **SeverityPill** [client] — props: severity — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **GuidesScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\firstaid\GuidesScreen.kt`
- **FirstAidCard** [client] — props: onExplore — `app\src\main\java\com\example\omni\ui\home\HomeBento.kt`
- **WaterCard** [client] — props: glasses, goal, onAdd — `app\src\main\java\com\example\omni\ui\home\HomeBento.kt`
- **WaterGlassRow** [client] — props: filled — `app\src\main\java\com\example\omni\ui\home\HomeBento.kt`
- **StepsCard** [client] — props: steps, goal, permissionNeeded, onEnableTracking — `app\src\main\java\com\example\omni\ui\home\HomeBento.kt`
- **HomeScreen** [client] — props: header — `app\src\main\java\com\example\omni\ui\home\HomeScreen.kt`
- **HomeGreeting** [client] — props: name — `app\src\main\java\com\example\omni\ui\home\HomeScreen.kt`
- **HomeScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\home\HomeScreen.kt`
- **UpdateCard** [client] — props: verticalPadding, innerGap — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **UpdateHeader** [client] — props: title, value, glyph, glyphWidth — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **UpdateProgress** [client] — props: trackHeight, lead, fill, tail — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **AnimatedUpdateProgress** [client] — props: trackHeight, progress — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **StatusChip** [client] — props: status — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **UpdateAxis** [client] — props: height, endLabel, marker, endInset, startLabel — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **FiberCard** [client] — props: grams, goalGrams — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **SleepCard** [client] — props: hours, goalHours, onLog — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **CprCard** [client] — props: percent — `app\src\main\java\com\example\omni\ui\home\HomeUpdates.kt`
- **SleepEntrySheet** [client] — props: visible, hours, onDismiss — `app\src\main\java\com\example\omni\ui\home\SleepEntrySheet.kt`
- **SleepSheet** [client] — props: initial, onDismiss — `app\src\main\java\com\example\omni\ui\home\SleepEntrySheet.kt`
- **StepButton** [client] — props: glyph, enabled, onClick — `app\src\main\java\com\example\omni\ui\home\SleepEntrySheet.kt`
- **ChatScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\messages\ChatScreen.kt`
- **MessageBubble** [client] — props: message, mine — `app\src\main\java\com\example\omni\ui\messages\ChatScreen.kt`
- **ChatInput** [client] — props: draft, canSend, onDraftChange — `app\src\main\java\com\example\omni\ui\messages\ChatScreen.kt`
- **ChatScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\ChatScreen.kt`
- **ChatScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\ChatScreen.kt`
- **MessagesScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **ConversationRow** [client] — props: conversation, onClick — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **NewMessagePicker** [client] — props: people, professionals, onStartWith — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **PickerGroup** [client] — props: label, rows, empty, onStartWith — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **SegmentPill** [client] — props: label, selected, onClick — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **Avatar** [client] — props: name, photoUrl, size — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **MessagesScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **MessagesScreenPickerPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **MessagesScreenPickerEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **MessagesScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **MessagesScreenDoctorPreview** [client] — `app\src\main\java\com\example\omni\ui\messages\MessagesScreen.kt`
- **NotificationsScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\notifications\NotificationsScreen.kt`
- **NotificationRow** [client] — props: item, onClick — `app\src\main\java\com\example\omni\ui\notifications\NotificationsScreen.kt`
- **NotificationsScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\notifications\NotificationsScreen.kt`
- **NotificationsScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\notifications\NotificationsScreen.kt`
- **AiMealSheet** [client] — props: visible, state, onAnalyze — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **LogMethodSheet** [client] — props: visible, onUseAi — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **InputContent** [client] — props: note, noteIsError, onAnalyze — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **AnalyzingContent** [client] — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **ReviewContent** [client] — props: analysis, onConfirm — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **ItemBreakdownRow** [client] — props: item, index — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **PrimaryButton** [client] — props: label, enabled, onClick — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **FieldLabel** [client] — props: text — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **DescriptionField** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\nutrition\AiMealSheet.kt`
- **MealEntrySheet** [client] — props: visible, editing, onDismiss — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **MealSheet** [client] — props: editing, onDismiss — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **SlotRow** [client] — props: selected, onSelect — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **LabelledField** [client] — props: label, value, unit, onValueChange — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **MacroField** [client] — props: label, value, onValueChange — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **FieldLabel** [client] — props: text — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **SheetField** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\nutrition\MealEntrySheet.kt`
- **MonthPickerSheet** [client] — props: visible, month, selected, calories, onDismiss — `app\src\main\java\com\example\omni\ui\nutrition\MonthPickerSheet.kt`
- **MonthSheet** [client] — props: month, selected, calories, onDismiss — `app\src\main\java\com\example\omni\ui\nutrition\MonthPickerSheet.kt`
- **DayCell** [client] — props: date, calories, selected, today, enabled, onClick — `app\src\main\java\com\example\omni\ui\nutrition\MonthPickerSheet.kt`
- **MonthArrow** [client] — props: forward, enabled, description, onClick — `app\src\main\java\com\example\omni\ui\nutrition\MonthPickerSheet.kt`
- **NutritionScreen** [client] — props: header — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **DayRow** [client] — props: week, selected, onSelect — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **DayChip** [client] — props: day, selected, enabled, onClick — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **GoalGauge** [client] — props: calories, calorieGoal — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **Measurement** [client] — props: value, unit — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **ArcGauge** [client] — props: progress — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **RingPager** [client] — props: state, pages — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **RingRow** [client] — props: rings — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **RingGauge** [client] — props: ring — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **PageDots** [client] — props: current, count — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **FoodLogHeader** [client] — props: onLog — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **FoodLogEntry** [client] — props: meal, onClick — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **MacroChip** [client] — props: amount, fill — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **NutritionScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\nutrition\NutritionScreen.kt`
- **DoctorConsultationScreen** [client] — props: viewModel, onBack — `app\src\main\java\com\example\omni\ui\omniplus\DoctorConsultationScreen.kt`
- **DoctorConsultationPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorConsultationScreen.kt`
- **DoctorConsultationEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorConsultationScreen.kt`
- **DoctorDirectoryScreen** [client] — props: state, onDoctorClick — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **DoctorCard** [client] — props: doctor, onClick — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **SpecialtyChip** [client] — props: label, selected, onClick — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **DoctorDirectoryPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **DoctorDirectoryLoadingPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **DoctorDirectoryEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorDirectoryScreen.kt`
- **DoctorProfileScreen** [client] — props: state, onStartConsultation — `app\src\main\java\com\example\omni\ui\omniplus\DoctorProfileScreen.kt`
- **ProfileStat** [client] — props: label, value — `app\src\main\java\com\example\omni\ui\omniplus\DoctorProfileScreen.kt`
- **SectionHeading** [client] — props: text — `app\src\main\java\com\example\omni\ui\omniplus\DoctorProfileScreen.kt`
- **DoctorProfilePreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorProfileScreen.kt`
- **DoctorProfileLoadingPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\DoctorProfileScreen.kt`
- **OmniPlusPaywallScreen** [client] — props: state, onPurchase — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **SubscribedScreen** [client] — props: message, onBrowseDoctors — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **PlanCard** [client] — props: pkg, selected, onClick — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **BestValueRibbon** [client] — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **FeatureCheckRow** [client] — props: text — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **OmniPlusPaywallPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **OmniPlusPaywallSubscribedPreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\OmniPlusPaywallScreen.kt`
- **PremiumGate** [client] — props: revenueCatRepository, onNavigateToPaywall — `app\src\main\java\com\example\omni\ui\omniplus\PremiumGate.kt`
- **GatePage** [client] — props: title, body, action, onAction — `app\src\main\java\com\example\omni\ui\omniplus\PremiumGate.kt`
- **PremiumGatePreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\PremiumGate.kt`
- **PremiumGateInactivePreview** [client] — `app\src\main\java\com\example\omni\ui\omniplus\PremiumGate.kt`
- **OnboardingScreen** [client] — props: onFinish — `app\src\main\java\com\example\omni\ui\onboarding\OnboardingScreen.kt`
- **OnboardingPageContent** [client] — props: page — `app\src\main\java\com\example\omni\ui\onboarding\OnboardingScreen.kt`
- **PageIndicator** [client] — props: pageCount, currentPage — `app\src\main\java\com\example\omni\ui\onboarding\OnboardingScreen.kt`
- **BottomActions** [client] — props: isLastPage, onNext — `app\src\main\java\com\example\omni\ui\onboarding\OnboardingScreen.kt`
- **OnboardingScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\onboarding\OnboardingScreen.kt`
- **ProfileScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\profile\ProfileScreen.kt`
- **ProfilePostCard** [client] — props: post, onOpen — `app\src\main\java\com\example\omni\ui\profile\ProfileScreen.kt`
- **ProfileScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\profile\ProfileScreen.kt`
- **ProfileScreenFollowRefusedPreview** [client] — `app\src\main\java\com\example\omni\ui\profile\ProfileScreen.kt`
- **GoalsScreen** [client] — props: state — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **GoalCard** [client] — props: row, onStep — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **StepButton** [client] — props: glyph, enabled, label, onClick — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **SaveButton** [client] — props: state, onSave — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **GoalsScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **GoalsScreenDirtyPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\GoalsScreen.kt`
- **PersonalInformationScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **BirthdayRow** [client] — props: state, onDayChange — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **GenderRow** [client] — props: selected, onSelect — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **GenderChip** [client] — props: label, chosen, onClick — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **SaveButton** [client] — props: state, onSave — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **AccountField** [client] — props: label, value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **DateBox** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **FieldBox** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **PersonalInformationScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **PersonalInformationScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\PersonalInformationScreen.kt`
- **SavedEmergenciesScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **ContactRow** [client] — props: contact, onCall — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **AddContactForm** [client] — props: state, onNameChange — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **ContactField** [client] — props: label, value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **AddContactButton** [client] — props: atCapacity, onClick — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **SavedEmergenciesScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **SavedEmergenciesScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **SavedEmergenciesScreenFormPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SavedEmergenciesScreen.kt`
- **SecurityScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **Notice** [client] — props: error, notice — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **ActionButton** [client] — props: label, busyLabel, enabled, busy, onClick — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **SecurityField** [client] — props: label, value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **SecurityScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **SecurityScreenNoticePreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SecurityScreen.kt`
- **SettingScreen** [client] — props: userName, userEmail, photoUrl, photoUploading, photoError, pushNotifications, offlineCache, onBack — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **AccountDetailsGroup** [client] — props: onPersonalInformation — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **EmergencyGroup** [client] — props: onSavedEmergencies — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **PreferencesGroup** [client] — props: onDailyGoals — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **ProfileRow** [client] — props: name, email, photoUrl, uploading, error, onChangePhoto — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **SettingsGroup** [client] — props: label — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **SettingsRow** [client] — props: title, subtitle, textWidth, onClick — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **PreferenceSwitch** [client] — props: checked, trackOn, onCheckedChange — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **HealthcareCard** [client] — props: onApply — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **OmniPlusCard** [client] — props: onClick — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **LogOutButton** [client] — props: onClick — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **SettingScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\SettingScreen.kt`
- **VerificationScreen** [client] — props: state — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **ApplicationForm** [client] — props: state, onProfessionChange — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **ProfessionChip** [client] — props: label, selected, onClick — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **LicenceField** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **SpecialtyField** [client] — props: value, onValueChange — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **SubmitButton** [client] — props: state, onSubmit — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **PendingCard** [client] — props: request — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **StatusCard** [client] — props: title, body — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **VerificationScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **VerificationScreenFilledPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **VerificationScreenPendingPreview** [client] — `app\src\main\java\com\example\omni\ui\settings\VerificationScreen.kt`
- **SleepConfirmSheet** [client] — props: date, onConfirm — `app\src\main\java\com\example\omni\ui\sleep\SleepConfirmSheet.kt`
- **SleepLogSheet** [client] — props: visible, editing, onDismiss — `app\src\main\java\com\example\omni\ui\sleep\SleepLogSheet.kt`
- **SleepSheetBody** [client] — props: editing, onDismiss — `app\src\main\java\com\example\omni\ui\sleep\SleepLogSheet.kt`
- **TimeStepper** [client] — props: label, display, onDown — `app\src\main\java\com\example\omni\ui\sleep\SleepLogSheet.kt`
- **StepButton** [client] — props: glyph, onClick — `app\src\main\java\com\example\omni\ui\sleep\SleepLogSheet.kt`
- **QualityChip** [client] — props: quality, selected, onClick — `app\src\main\java\com\example\omni\ui\sleep\SleepLogSheet.kt`
- **SleepScreen** [client] — props: state, onBack — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **BackRow** [client] — props: onBack — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **NightCard** [client] — props: selectedDate, record, onPrevDay — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **InsightsCard** [client] — props: weeklyAverageMinutes, record, goalMinutes — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **HistorySection** [client] — props: visibleMonth, records, selectedDate, onPrevMonth — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **HistoryRow** [client] — props: record, selected, onClick — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **DeleteConfirmSheet** [client] — props: date, onConfirm — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **PagerArrow** [client] — props: glyph, enabled, onClick — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **PrimaryButton** [client] — props: label, onClick — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **ErrorBanner** [client] — props: message, onDismiss — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **SleepScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **SleepScreenEmptyPreview** [client] — `app\src\main\java\com\example\omni\ui\sleep\SleepScreen.kt`
- **OmniMap** [client] — props: hospitals, selectedId, nearestId, userLocation, route, recenterTick, onSelectHospital — `app\src\main\java\com\example\omni\ui\sos\OmniMap.kt`
- **SosScreen** [client] — props: header — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **MapSearchField** [client] — props: query, onQueryChange — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **NoticeBanner** [client] — props: notice — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **RecenterButton** [client] — props: onClick — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SosSwipeTrack** [client] — props: onActivate — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **HospitalSheet** [client] — props: hospitals, selectedId, nearestId, hasLocation, routeStatus, query, directorySize, contactCount, sheetHeight, onDismiss — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **EmptyRail** [client] — props: message, icon, label, fill, onClick — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **HospitalCard** [client] — props: hospital, isNearest, routeStatus, onSelect — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **HospitalAction** [client] — props: icon, label, fill, labelColor, labelStart, enabled, onClick — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SosScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SosScreenSheetPreview** [client] — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SosScreenEmptyDirectoryPreview** [client] — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SosScreenLocationBlockedPreview** [client] — `app\src\main\java\com\example\omni\ui\sos\SosScreen.kt`
- **SplashScreen** [client] — `app\src\main\java\com\example\omni\ui\SplashScreen.kt`
- **SplashScreenPreview** [client] — `app\src\main\java\com\example\omni\ui\SplashScreen.kt`
- **OmniTheme** [client] — `app\src\main\java\com\example\omni\ui\theme\Theme.kt`

---

# Libraries

- `backend\src\comment.ts` — function createCommentHandler: (deps) => void, interface CommentDeps
- `backend\src\config.ts` — function validateConfig: () => void, const config
- `backend\src\email-template.ts` — function generateOtpEmailHtml: (data) => string, function generateOtpEmailText: (data) => string
- `backend\src\fcm.ts`
  - class FirestoreFcmService
  - class NoOpFcmService
  - interface FcmService
- `backend\src\gemini.ts`
  - function describeGeminiFailure: (bodyText) => string
  - class GeminiUnavailableError
  - class GeminiRateLimitError
  - class RestGeminiClient
  - interface GeminiClient
  - const MEAL_SYSTEM_PROMPT
  - _...1 more_
- `backend\src\like.ts` — function createLikeHandler: (deps) => void, interface LikeDeps
- `backend\src\meal-analyze.ts`
  - function normalizeUnit: (unit) => QuantityKind
  - function maxQuantityForUnit: (unit) => number
  - function analyzeMealSchema: (maxTextLength) => void
  - function parseGeminiJson: (raw) => unknown
  - function validateAnalysis: (raw) => MealAnalysisResult
  - function computeTotals: (items) => MealTotals
  - _...3 more_
- `backend\src\message.ts` — function createMessageHandler: (deps) => void, interface MessageDeps
- `backend\src\rate-limiter.ts`
  - class FirestoreRateLimiter
  - interface RateLimitConfig
  - interface RateLimitInfo
  - interface RateLimitError
- `backend\src\revenuecat.ts`
  - function createRevenueCatService: (deps) => RevenueCatService
  - interface RevenueCatDeps
  - interface RevenueCatService
- `backend\src\sendgrid.ts`
  - function initializeSendGrid: () => void
  - function isSendGridConfigured: () => boolean
  - function sendOtpEmail: (data) => Promise<void>
- `backend\src\utils.ts`
  - function generateOtp: () => string
  - function hashOtp: (otp) => string
  - function verifyOtpHash: (otp, hash) => boolean
  - function generateEmailVerificationLink: (auth, email, firebaseHostingUrl) => Promise<string>
  - function formatOtpForDisplay: (otp) => string
  - function getRemainingSeconds: (expiresAt) => number
  - _...1 more_
- `backend\src\verification.ts` — function createVerificationHandlers: (deps) => void, interface VerificationDeps
- `scripts\navbar-fix.py` — function replace: (path, old, new) -> None

---

# Config

## Environment Variables

- `ADMIN_BOOTSTRAP_UID` **required** — backend\src\index.ts
- `DOCTOR_COMP_DURATION` (has default) — backend\src\config.ts
- `FIREBASE_HOSTING_URL` (has default) — backend\.env.example
- `FIREBASE_SERVICE_ACCOUNT` **required** — backend\.env.example
- `GEMINI_API_KEY` **required** — backend\.env.example
- `GEMINI_MODEL` (has default) — backend\diagnose-multi-item.js
- `GOOGLE_APPLICATION_CREDENTIALS` **required** — tools\seed-hospitals.mjs
- `PORT` (has default) — backend\.env.example
- `REVENUECAT_SECRET_API_KEY` **required** — backend\src\config.ts
- `SENDGRID_API_KEY` **required** — backend\.env.example
- `SENDGRID_FROM_EMAIL` (has default) — backend\.env.example

## Config Files

- `backend\.env.example`

---

# Middleware

## rate-limit
- rate-limiter — `backend\src\rate-limiter.ts`

## cors
- cors — `backend\index.js`

## auth
- authenticate — `backend\src\index.ts`

---

# Dependency Graph

## Most Imported Files (change these carefully)

- `backend\src\types.ts` — imported by **8** files
- `backend\src\fcm.ts` — imported by **6** files
- `backend\src\utils.ts` — imported by **3** files
- `backend\src\email-template.ts` — imported by **3** files
- `backend\src\verification.ts` — imported by **3** files
- `backend\src\config.ts` — imported by **2** files
- `backend\src\gemini.ts` — imported by **2** files
- `backend\src\revenuecat.ts` — imported by **2** files
- `backend\src\like.ts` — imported by **2** files
- `backend\src\comment.ts` — imported by **2** files
- `backend\src\message.ts` — imported by **2** files
- `backend\src\sendgrid.ts` — imported by **1** files
- `backend\src\rate-limiter.ts` — imported by **1** files
- `backend\src\meal-analyze.ts` — imported by **1** files

## Import Map (who imports what)

- `backend\src\types.ts` ← `backend\src\comment.ts`, `backend\src\email-template.ts`, `backend\src\index.ts`, `backend\src\like.ts`, `backend\src\meal-analyze.ts` +3 more
- `backend\src\fcm.ts` ← `backend\src\comment.ts`, `backend\src\index.ts`, `backend\src\like.ts`, `backend\src\message.ts`, `backend\src\verification.ts` +1 more
- `backend\src\utils.ts` ← `backend\src\email-template.ts`, `backend\src\index.ts`, `backend\tests\otp.test.ts`
- `backend\src\email-template.ts` ← `backend\src\index.ts`, `backend\src\sendgrid.ts`, `backend\tests\otp.test.ts`
- `backend\src\verification.ts` ← `backend\src\index.ts`, `backend\tests\doctor-verification.test.ts`, `backend\tests\phase12r.test.ts`
- `backend\src\config.ts` ← `backend\src\index.ts`, `backend\src\sendgrid.ts`
- `backend\src\gemini.ts` ← `backend\src\index.ts`, `backend\tests\meal-analyze.test.ts`
- `backend\src\revenuecat.ts` ← `backend\src\index.ts`, `backend\src\verification.ts`
- `backend\src\like.ts` ← `backend\src\index.ts`, `backend\tests\phase12r.test.ts`
- `backend\src\comment.ts` ← `backend\src\index.ts`, `backend\tests\phase12r.test.ts`

---

_Generated by [codesight](https://github.com/Houseofmvps/codesight) — see your codebase clearly_