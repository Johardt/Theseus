package me.johardt.theseus.client;

import me.johardt.theseus.client.theme.ClientTheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientThemeTest {
    @Test
    void parsesAllThemeGroups() {
        ClientTheme theme = ClientTheme.parse("""
            {
              "questTree": {
                "headerTitle": "#010203",
                "headerGroupsTitle": "#040506",
                "groupName": "#070809",
                "grid": "#0A0B0C"
              },
              "questDetails": {
                "taskTitle": "#0A0B0C",
                "taskDescription": "#0D0E0F",
                "taskProgress": "#101112",
                "taskNestedTitle": "#131415",
                "taskSubmit": "#161718",
                "taskSubmitDisabled": "#191A1B",
                "rewardTitle": "#1C1D1E",
                "rewardDescription": "#1F2021",
                "taskRewardStatusHeading": "#222324",
                "summaryTitle": "#252627",
                "summaryProgress": "#28292A",
                "summaryDescription": "#2B2C2D",
                "tabButton": "#2E2F30",
                "tabButtonSelected": "#313233"
              },
              "tracker": {
                "title": "#343536",
                "quest": "#373839",
                "task": "#3A3B3C",
                "progress": "#3D3E3F",
                "completed": "#404142"
              },
              "toasts": {
                "title": "#434445",
                "claimedTitle": "#464748",
                "tutorialTitle": "#494A4B",
                "content": "#4C4D4E",
                "tutorialContent": "#4F5051",
                "keybinding": "#525354"
              },
              "modals": {
                "title": "#555657",
                "rewardsAmount": "#58595A",
                "background": "#5B5C5D",
                "border": "#5E5F60",
                "error": "#616263"
              },
              "editor": {
                "modalUploadingTitle": "#646566",
                "modalDependenciesTitle": "#676869",
                "modalIconsTitle": "#6A6B6C",
                "modalTextTitle": "#6D6E6F",
                "modalUploadingFileName": "#707172",
                "modalDependenciesDependencyTitle": "#737475",
                "modalUploadingFileSize": "#767778",
                "modalEditSettingTitle": "#797A7B",
                "error": "#7C7D7E"
              },
              "genericControls": {
                "buttonActive": "#7F8081",
                "buttonInactive": "#828384",
                "buttonHover": "#858687",
                "text": "#88898A",
                "accent": "#8B8C8D",
                "error": "#8E8F90"
              }
            }
            """);

        assertEquals(0xFF010203, theme.questTree().headerTitle());
        assertEquals(0xFF0A0B0C, theme.questTree().grid());
        assertEquals(0xFF313233, theme.questDetails().tabButtonSelected());
        assertEquals(0xFF404142, theme.tracker().completed());
        assertEquals(0xFF616263, theme.modals().error());
        assertEquals(0xFF8B8C8D, theme.genericControls().accent());
    }

    @Test
    void partialGroupsInheritEveryUnspecifiedField() {
        ClientTheme theme = ClientTheme.parse("""
            {
              "tracker": {"quest": "#123456"},
              "modals": {"title": "#ABCDEF"}
            }
            """);

        assertEquals(0xFF123456, theme.tracker().quest());
        assertEquals(ClientTheme.DEFAULT.tracker().title(), theme.tracker().title());
        assertEquals(ClientTheme.DEFAULT.tracker().completed(), theme.tracker().completed());
        assertEquals(0xFFABCDEF, theme.modals().title());
        assertEquals(ClientTheme.DEFAULT.modals().border(), theme.modals().border());
        assertEquals(ClientTheme.DEFAULT.questDetails(), theme.questDetails());
    }

    @Test
    void invalidFieldsFallBackLocally() {
        ClientTheme theme = ClientTheme.parse("""
            {
              "tracker": {
                "title": "not-a-color",
                "quest": "#010203"
              },
              "genericControls": {
                "accent": [],
                "text": "#040506"
              }
            }
            """);

        assertEquals(ClientTheme.DEFAULT.tracker().title(), theme.tracker().title());
        assertEquals(0xFF010203, theme.tracker().quest());
        assertEquals(ClientTheme.DEFAULT.genericControls().accent(), theme.genericControls().accent());
        assertEquals(0xFF040506, theme.genericControls().text());
        assertEquals(ClientTheme.DEFAULT.questTree(), theme.questTree());
    }

    @Test
    void malformedRootFallsBackToDefault() {
        assertSame(ClientTheme.DEFAULT, ClientTheme.parse("["));
        assertSame(ClientTheme.DEFAULT, ClientTheme.parse("[]"));
        assertSame(ClientTheme.DEFAULT, ClientTheme.parse("null"));
    }

    @Test
    void retainedSectionNamesRemainCompatible() {
        ClientTheme theme = ClientTheme.parse("""
            {
              "questsScreen": {"headerTitle": "#111111"},
              "questScreen": {"summaryTitle": "#222222"},
              "pinnedQuests": {"task": {"r": 51, "g": 51, "b": 51, "a": 255}},
              "generic": {"buttonInactive": "#444444"}
            }
            """);

        assertEquals(0xFF111111, theme.questTree().headerTitle());
        assertEquals(0xFF222222, theme.questDetails().summaryTitle());
        assertEquals(0xFF333333, theme.tracker().task());
        assertEquals(0xFF444444, theme.genericControls().buttonInactive());
    }

    @Test
    void preferredSectionWinsOverLegacyAlias() {
        ClientTheme theme = ClientTheme.parse("""
            {
              "tracker": {"quest": "#111111"},
              "pinnedQuests": {"quest": "#222222"}
            }
            """);

        assertEquals(0xFF111111, theme.tracker().quest());
    }
}
