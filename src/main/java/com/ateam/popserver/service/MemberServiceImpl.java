package com.ateam.popserver.service;

import com.ateam.popserver.dto.MemberDTO;
import com.ateam.popserver.model.Auth;
import com.ateam.popserver.model.Member;
import com.ateam.popserver.model.Wallet;
import com.ateam.popserver.net.JsonParsing;
import com.ateam.popserver.persistence.AuthRepository;
import com.ateam.popserver.persistence.MemberRepository;
import com.ateam.popserver.persistence.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService, UserDetailsService {
	private final WalletRepository walletRepository;
	private final MemberRepository memberRepository;

	private final AuthRepository authRepository;

	private final PasswordEncoder passwordEncoder;
	
	

	@Override
	public Map<String, String> validateHandling(Errors errors) {
		Map<String, String> validatorResult = new HashMap<>();         
		
		for (FieldError error : errors.getFieldErrors()) {            
			String validKeyName = String.format("valid_%s", error.getField());            
			validatorResult.put(validKeyName, error.getDefaultMessage());        
			System.out.println("validKeyName :: "+validKeyName);
		}        
		return validatorResult;
	}

	private Member getMember(String mid) {
		Optional<Member> optMember = memberRepository.findMemberByMid(mid);

		if(optMember.isEmpty()) {
			return null;
		}

		return optMember.get();
	}
	
	@Override
	public String[] registerMember(MemberDTO dto) {
		//id, ????????? ?????? ?????? - ????????? ????????? ??? ??????
		Member member1 = memberRepository.findByMid(dto.getMid());
		Member member2 = memberRepository.findByNickname(dto.getNickname());

		if(member1 != null ||member2 != null) {
			return new String[] {"id??????", "nickname??????"};
		}
		
		//alias??? ????????????????????? ????????? walletid ???????????? save
		String alias=dto.getAlias();
		HashMap<String, Object> req=new HashMap<String, Object>(); 
		req.put("Alias", dto.getAlias());
		req.put("Address", "");
		//????????? ??????????????? jsonString?????? ObjectMapper ????????? ????????? ????????? address?????? ???????????? value ????????????
		try {
			String walletId = JsonParsing.postRequest("http://localhost:3030/MakeWallet", req);
			if(walletId==null) {
				System.out.println("walletid ?????? ?????????;;");
			}
			Wallet wallet = Wallet.builder().walletid(walletId).balance(0).build();
			walletRepository.save(wallet);
			dto.setWallet(wallet);

			dto.setPw(passwordEncoder.encode(dto.getPw()));
			
			Member member = dtoToEntity(dto);
			
			System.out.println("????????? member :: "+member);
			System.out.println("walletid ::" + member.getWallet().getWalletid());
			System.out.println("wnum ::" + member.getWallet().getWnum());

			Auth auth = Auth.builder().member(member).aname("ROLE_USER").build();

			authRepository.save(auth);
			memberRepository.save(member);

			return new String[] {member.getMid(), member.getWallet().getWalletid()};
		}catch(Exception e) {
			e.printStackTrace();
		}
		return new String[] {dto.getMid()};	//?????? ??????
	}

	@Override
	public MemberDTO getMember(MemberDTO memberDTO) {
				Optional <Member> optional = 
						memberRepository.findById(memberDTO.getMnum());
				//???????????? id
				if(optional.isPresent()) {
					//???????????? ??????
					Member member = optional.get();
					return entityToDto(member);
				}else {
					return null;
				}
	}

	@Override
	public MemberDTO getMemberByWalletId(String walletid) {
		//

		Wallet w = walletRepository.findByWalletid(walletid);
		Member m = new Member();
		if(w!=null) {
			m = memberRepository.findByWallet(w);
		}

		return entityToDto(m);
	}

	@Override
	public String updateMember(MemberDTO dto) {
		String res = "";
		//????????? ????????????
		Optional<Member> result = memberRepository.findById(dto.getMnum());
			if(result.isPresent()) {
				Member member = result.get();
				member.changePw(dto.getPw());
				member.changeNickname(dto.getNickname());
				member.changeTel(dto.getTel());
				member.changeAddr(dto.getAddr());
				memberRepository.save(member);
				res=member.getMid();
				return res;
			}
		return null;		//????????? ???????????? ?????? ?????? ????????? ??? ?????????
	}

	@Override
	public String deleteMember(MemberDTO dto) {
		Member member = dtoToEntity(dto);
		memberRepository.delete(member);
		return member.getMid();
	}

	@Override
	@Transactional
	public UserDetails loadUserByUsername(String mid) throws UsernameNotFoundException {
		Member member = memberRepository.findByMid(mid);

		if (member == null) {
			throw new UsernameNotFoundException("????????? ????????? ????????????.");
		}

		System.out.println(authRepository.getAuthList(member));
		List<SimpleGrantedAuthority> sga =
				authRepository.getAuthList(member).stream()
						.map(SimpleGrantedAuthority::new).collect(Collectors.toList());

		member.setAuthorities(sga);

		return member;
	}

	@Override
	public MemberDTO getMemberDto(Long mnum) {
		MemberDTO dto = entityToDto(memberRepository.findById(mnum).get());
		return dto;
	}

	@Override
	public MemberDTO getMemberByMnum(Long mnum) {
		Optional<Member> m = memberRepository.findById(mnum);

		return m.isPresent()?entityToDto(m.get()):null;
	}

}
