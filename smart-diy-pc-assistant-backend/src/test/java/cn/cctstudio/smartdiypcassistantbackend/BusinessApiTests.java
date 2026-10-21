package cn.cctstudio.smartdiypcassistantbackend;

import cn.cctstudio.smartdiypcassistantbackend.hardware.*;
import cn.cctstudio.smartdiypcassistantbackend.hardware.dto.*;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.*;
import cn.cctstudio.smartdiypcassistantbackend.buildconfig.dto.*;
import cn.cctstudio.smartdiypcassistantbackend.auth.AuthService;
import cn.cctstudio.smartdiypcassistantbackend.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class BusinessApiTests {
    @Autowired HardwarePartService hardware;
    @Autowired BuildConfigService builds;
    @Autowired AuthService auth;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired cn.cctstudio.smartdiypcassistantbackend.user.UserRepository users;
    @Autowired cn.cctstudio.smartdiypcassistantbackend.security.JwtService jwt;

    @Test void roleChangesApplyToExistingTokens() throws Exception {
        var user = users.save(new cn.cctstudio.smartdiypcassistantbackend.user.User("role_test", "role@test.local", "unused", cn.cctstudio.smartdiypcassistantbackend.user.UserRole.ADMIN));
        String token = jwt.createToken(new cn.cctstudio.smartdiypcassistantbackend.security.UserPrincipal(user.getId(), user.getUsername(), user.getRole()));
        user.changeRole(cn.cctstudio.smartdiypcassistantbackend.user.UserRole.USER);
        users.save(user);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void agentCanReadCatalogWithoutCategoryParameter() throws Exception {
        var user = auth.adminLogin(new LoginRequest("admin", "123456"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/hardware-parts")
                .header("Authorization", "Bearer " + user.token()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(0));
    }

    HardwarePartResponse part(HardwarePartType type,String model,String form,String supported,int length,int height) {
        return hardware.create(new HardwarePartRequest(type,"",model,0,0,"AM5","DDR5",form,650,length,height,supported,"integration test",true));
    }

    @Test void hardwareDimensionsSurviveSaveAndRead() {
        var result=part(HardwarePartType.CASE,"test dimensions case","ATX","ATX,MATX",330,165);
        assertThat(result.gpuLengthMm()).isEqualTo(330);
        assertThat(result.coolerHeightMm()).isEqualTo(165);
        assertThat(hardware.listAdmin().stream().filter(p->p.id().equals(result.id())).findFirst().orElseThrow().gpuLengthMm()).isEqualTo(330);
    }

    @Test void atxDoesNotMatchMatxBySubstring() {
        var board=part(HardwarePartType.MOTHERBOARD,"test ATX board","ATX","",0,0);
        var pcCase=part(HardwarePartType.CASE,"test MATX case","MATX","MATX",0,0);
        assertThat(builds.validate(List.of(board.id(),pcCase.id())).status()).isEqualTo("BLOCK");
    }

    @Test void configurationsKeepSnapshotsWithoutNetworkOrAi() {
        var user=auth.adminLogin(new LoginRequest("admin","123456"));
        var storage=part(HardwarePartType.STORAGE,"test snapshot SSD","","",0,0);
        var request=new BuildConfigRequest("snapshot test","",List.of(storage.id()),List.of(new BuildPartSelectionRequest(storage.id(),"Recorded product",499,"","https://item.jd.com/123.html","123",storage.model())),BuildConfigSource.MANUAL);
        var saved=builds.create(user.userId(),request);
        var read=builds.get(user.userId(),saved.id());
        assertThat(read.totalPrice()).isEqualTo(499);
        assertThat(read.parts().get(0).productTitle()).isEqualTo("Recorded product");
        assertThat(read.serialNumber()).startsWith("#");
    }
}
