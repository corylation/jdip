package dip.misc;
import org.jcodec.common.*;
import org.jcodec.containers.mp4.muxer.FramesMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.common.model.*;
import org.jcodec.containers.mp4.MP4Packet;

import javax.imageio.*;
import java.awt.image.*;
import java.io.File;
import java.io.*;
import java.nio.ByteBuffer;

public class SequenceMuxer {
    private SeekableByteChannel ch;
    private FramesMP4MuxerTrack outTrack;
    private int frameNo = 0;
    private MP4Muxer muxer;
    private Size size;

    public SequenceMuxer(File out) throws IOException {
        this.ch = NIOUtils.writableFileChannel(out);

        // Muxer that will store the encoded frames
        muxer = new MP4Muxer(ch, Brand.MP4);

        // Add video track to muxer
        outTrack = muxer.addTrack(TrackType.VIDEO, 25);
    }

    public void encodeImage(ByteBuffer in) throws IOException {
        if (size == null) {
            BufferedImage read = ImageIO.read(new ByteArrayInputStream(in.array()));
            size = new Size(read.getWidth(), read.getHeight());
        }
        // Add packet to video track
        int secondsPerGamePhase = 20;
        outTrack.addFrame(new MP4Packet(
            in, // Image to Add
            frameNo * secondsPerGamePhase,
            1, 
            secondsPerGamePhase,
            frameNo, 
            true, 
            null, 
            frameNo * secondsPerGamePhase, 
            0
        ));

        frameNo++;
    }

    public void finish() throws IOException {
        // Push saved SPS/PPS to a special storage in MP4
        outTrack.addSampleEntry(MP4Muxer.videoSampleEntry("png ", size, "JCodec"));

        // Write MP4 header and finalize recording
        muxer.writeHeader();
        NIOUtils.closeQuietly(ch);
    }
}