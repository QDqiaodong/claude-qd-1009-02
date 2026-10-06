package com.karaoke.lounge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Song;
import com.karaoke.lounge.repository.SongRepository;

/**
 * 歌曲库业务。
 *
 * <p>规则：① 歌曲编码唯一；② 语种须为合法枚举；③ 时长须 &gt; 0。</p>
 */
@Service
public class SongService {

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /**
     * 查询歌曲，语种参数三态口径明确：
     * <ul>
     *   <li>不传 language（null）：明确的「全部」状态，返回全库；</li>
     *   <li>传空白串：属于参数错误（不能被静默当作「全部」，否则调用方无法区分是无结果还是参数错误）；</li>
     *   <li>传非白名单语种：参数错误，错误信息中给出合法取值。</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<Song> list(String language) {
        if (language == null) {
            return songRepository.findAll();
        }
        String normalized = language.trim();
        if (normalized.isEmpty()) {
            throw new BizException("歌曲语种参数不能为空白：如需查询全部歌曲，请省略 language 参数");
        }
        if (!Dict.isValidSongLanguage(normalized)) {
            throw new BizException("歌曲语种不合法：" + language + "，应为 " + Dict.SONG_LANGUAGES
                    + " 之一；如需查询全部歌曲，请省略 language 参数");
        }
        return songRepository.findByLanguage(normalized);
    }

    @Transactional(readOnly = true)
    public Song get(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new BizException("歌曲不存在：" + id));
    }

    @Transactional
    public Song create(Song input) {
        if (input.getSongCode() == null || input.getSongCode().isBlank()) {
            throw new BizException("歌曲编码不能为空");
        }
        if (songRepository.existsBySongCode(input.getSongCode().trim())) {
            throw new BizException("歌曲编码已存在：" + input.getSongCode());
        }
        if (input.getSongName() == null || input.getSongName().isBlank()) {
            throw new BizException("歌曲名称不能为空");
        }
        if (input.getLanguage() != null && !input.getLanguage().isBlank()
                && !Dict.isValidSongLanguage(input.getLanguage())) {
            throw new BizException("歌曲语种不合法：" + input.getLanguage() + "，应为 " + Dict.SONG_LANGUAGES);
        }
        if (input.getDurationSec() != null && input.getDurationSec() <= 0) {
            throw new BizException("歌曲时长必须大于 0");
        }
        Song song = new Song();
        song.setSongCode(input.getSongCode().trim());
        song.setSongName(input.getSongName().trim());
        song.setSinger(input.getSinger() == null ? "" : input.getSinger().trim());
        song.setLanguage(input.getLanguage() == null ? "国语" : input.getLanguage());
        song.setDurationSec(input.getDurationSec());
        return songRepository.save(song);
    }
}
