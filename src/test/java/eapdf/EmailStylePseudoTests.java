package eapdf;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailStylePseudoTests {
  private static String scoped(String rules) {
    return EmailStyles.scoped(Jsoup.parse("<style>" + rules + "</style><div><a href='https://example.org'>Link</a></div>"), new EmailRecord());
  }

  @Test void stateSelectorsRemainConditional() {
    String css = scoped("a:hover{color:red}a:active{color:blue}a:focus{color:green}a:visited{color:purple}a:link{color:black}");
    for (String state : new String[]{"hover", "active", "focus", "visited", "link"})
      assertTrue(css.contains("#email-root a:" + state + " {"), css);
    assertFalse(css.contains("#email-root a {"), css);
  }

  @Test void ancestorAndChildStatesStayOnTheirOwnCompound() {
    String css = scoped("table:hover a:focus{color:red}div:active > a:visited span{color:blue}");
    assertTrue(css.contains("#email-root table:hover a:focus {"), css);
    assertTrue(css.contains("#email-root div:active > a:visited span {"), css);
    assertFalse(css.contains("span:active"), css);
  }

  @Test void compoundStatesAndPseudoElementsRemainTogether() {
    String css = scoped("a:hover:focus::before{content:'state'}a:link:hover{color:red}");
    assertTrue(css.contains("#email-root a:hover:focus::before {"), css);
    assertTrue(css.contains("#email-root a:link:hover {"), css);
  }

  @Test void pseudoOnlyCompoundsDoNotDisappear() {
    String css = scoped(":hover > :focus{color:red}");
    assertTrue(css.contains("#email-root *:hover > *:focus {"), css);
    assertFalse(css.contains("#email-root * {"), css);
  }

  @Test void spacesAndCombinatorsInsideAttributeValuesRemainIntact() {
    String css = scoped("a[data-label='two words > end']:hover > span:nth-child(odd){color:red}");
    assertTrue(css.contains("two words > end"), css);
    assertTrue(css.contains(":hover > span:nth-child("), css);
    assertFalse(css.contains("span:hover"), css);
  }

  @Test void staticStructuralAndLinkConditionsRemainScoped() {
    String css = scoped("body li:first-child a:link::after{content:'link'}li:nth-child(odd):lang(en) a{color:blue}");
    assertTrue(css.contains("#email-root .ea-email-body li:first-child a:link::after {"), css);
    assertTrue(css.contains(":nth-child("), css);
    assertTrue(css.contains(":lang("), css);
  }
}
